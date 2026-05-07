const https = require('https');

const ROUTES_HOST = 'routes.googleapis.com';
const ROUTES_PATH = '/directions/v2:computeRoutes';
const MAX_ROUTE_CALLS = 12;
const STEP_SECONDS = 300;

async function buildCommutePlan(context) {
  if (!context.nextMeetingMinutes || context.nextMeetingMinutes > 180) return null;

  const rawDestination = context.nextMeetingLocation || context.destination || '';
  const destination = await resolveDestination(rawDestination);
  const origin = context.currentLocation;
  const fallback = unavailablePlan(context, destination?.label || context.nextMeetingLocation || context.destination);

  if (!process.env.GOOGLE_MAPS_API_KEY || !destination || !origin?.lat || !origin?.lng) {
    return {
      ...fallback,
      source: 'maps_unavailable',
      reason: !process.env.GOOGLE_MAPS_API_KEY
        ? 'Google Maps API key is not configured; real commute data unavailable'
        : 'Current location or coordinate destination missing; real commute data unavailable'
    };
  }

  try {
    const stressBuffer = context.sleepDebtMinutes >= 60 || context.meetingLoadToday >= 5 ? 15 : 8;
    const route = await fetchRouteDetails({
      origin: normalizeLatLng(origin),
      destination,
      departureIso: epochToIso(roundToNearestFiveMinutes(nowEpochSeconds())),
      apiKey: process.env.GOOGLE_MAPS_API_KEY
    });
    const etaMinutes = Math.ceil(route.durationSeconds / 60);
    const leaveInMinutes = Math.max(0, context.nextMeetingMinutes - etaMinutes - stressBuffer);
    const leaveEpoch = nowEpochSeconds() + leaveInMinutes * 60;
    const arrivalEpoch = nowEpochSeconds() + Math.max(0, context.nextMeetingMinutes) * 60;
    return {
      source: 'google_routes_api',
      destination: destination.label,
      etaMinutes,
      bufferMinutes: stressBuffer,
      leaveInMinutes,
      leaveByLocal: localTimeFromEpoch(leaveEpoch * 1000, context.timezone),
      shouldAlert: leaveInMinutes <= 30,
      reason: `Google Routes API traffic-aware drive ETA ${etaMinutes}m plus ${stressBuffer}m buffer`,
      modes: [
        {
          id: 'car',
          label: 'Car',
          mode: 'DRIVE',
          available: true,
          distanceText: route.distanceText || 'Traffic-aware route',
          durationMinutes: etaMinutes,
          durationText: `${etaMinutes} min`,
          leaveInMinutes,
          leaveByLocal: localTimeFromEpoch(leaveEpoch * 1000, context.timezone),
          arrivalByLocal: localTimeFromEpoch(arrivalEpoch * 1000, context.timezone)
        }
      ],
      directions: route.steps,
      polyline: route.polyline,
      mapsUrl: mapsUrl(origin, destination.label),
      routeDebug: {
        calls: 1,
        roundedDepartureIso: epochToIso(roundToNearestFiveMinutes(nowEpochSeconds()))
      }
    };
  } catch (error) {
    return {
      ...fallback,
      source: 'maps_error',
      reason: `Google Routes API unavailable: ${error.message}`
    };
  }
}

async function fetchRouteDetails({ origin, destination, departureIso, apiKey }) {
  const body = JSON.stringify({
    origin: { location: { latLng: { latitude: origin.lat, longitude: origin.lng } } },
    destination: { location: { latLng: { latitude: destination.lat, longitude: destination.lng } } },
    travelMode: 'DRIVE',
    routingPreference: 'TRAFFIC_AWARE',
    departureTime: departureIso,
    languageCode: 'en-IN'
  });
  const response = await postJsonWithRetry({
    host: ROUTES_HOST,
    path: ROUTES_PATH,
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(body),
      'X-Goog-Api-Key': apiKey,
      'X-Goog-FieldMask': 'routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.legs.steps.navigationInstruction.instructions,routes.legs.steps.distanceMeters,routes.legs.steps.staticDuration'
    },
    body
  });
  const route = response.routes?.[0];
  const durationSeconds = route?.duration ? parseDurationSeconds(route.duration) : 0;
  if (!durationSeconds) throw new Error(response.error?.message || 'routes_duration_missing');
  return {
    durationSeconds,
    distanceText: route?.distanceMeters ? `${Math.round(route.distanceMeters / 100) / 10} km` : '',
    polyline: route?.polyline?.encodedPolyline || null,
    steps: (route?.legs?.[0]?.steps || []).slice(0, 8).map(step => ({
      instruction: step.navigationInstruction?.instructions || 'Continue',
      distanceText: step.distanceMeters ? `${Math.round(step.distanceMeters / 100) / 10} km` : '',
      durationText: step.staticDuration ? `${Math.ceil(parseDurationSeconds(step.staticDuration) / 60)} min` : '',
      travelMode: 'DRIVE'
    }))
  };
}

async function latestDepartureByRoutesApi({ origin, destination, deadlineEpoch, apiKey }) {
  let left = roundToNearestFiveMinutes(nowEpochSeconds());
  let right = roundToNearestFiveMinutes(deadlineEpoch);
  const cache = new Map();
  let calls = 0;

  async function durationAt(epochSeconds) {
    const rounded = roundToNearestFiveMinutes(epochSeconds);
    if (cache.has(rounded)) return cache.get(rounded);
    if (calls >= MAX_ROUTE_CALLS) throw new Error('routes_api_call_limit_exceeded');
    calls += 1;
    const duration = await fetchRoutesDurationSeconds({
      origin,
      destination,
      departureIso: epochToIso(rounded),
      apiKey
    });
    cache.set(rounded, duration);
    return duration;
  }

  async function isValid(epochSeconds) {
    const duration = await durationAt(epochSeconds);
    return epochSeconds + duration <= deadlineEpoch;
  }

  if (right < left) right = left;

  while (right - left > 600 && calls < MAX_ROUTE_CALLS) {
    const mid = roundToNearestFiveMinutes(Math.floor((left + right) / 2));
    const valid = await isValid(mid);
    if (valid) left = mid;
    else right = mid;
    if (Math.abs(right - left) <= 60) break;
  }

  if (right - left <= 600) {
    for (let candidate = right; candidate >= left && calls < MAX_ROUTE_CALLS; candidate -= STEP_SECONDS) {
      if (await isValid(candidate)) {
        left = candidate;
        break;
      }
    }
  }

  const durationSeconds = await durationAt(left);
  return {
    departureEpoch: left,
    durationSeconds,
    calls
  };
}

async function resolveDestination(value) {
  const parsed = parseDestination(value);
  if (parsed) return parsed;
  const text = String(value || '').trim();
  if (!text || !process.env.GOOGLE_MAPS_API_KEY) return null;
  return geocodeAddress(text, process.env.GOOGLE_MAPS_API_KEY);
}

async function geocodeAddress(address, apiKey) {
  const places = await searchPlaces(address, apiKey);
  const result = places[0];
  if (!result) throw new Error('ZERO_RESULTS');
  return result;
}

async function searchPlaces(query, apiKey) {
  const address = String(query || '').trim();
  if (!address) return [];
  const params = new URLSearchParams({ address, key: apiKey });
  const json = await getJsonWithRetry({
    host: 'maps.googleapis.com',
    path: `/maps/api/geocode/json?${params.toString()}`
  });
  if (json.status === 'ZERO_RESULTS') return [];
  if (json.status !== 'OK') throw new Error(json.error_message || json.status || 'geocode_failed');
  return (json.results || [])
    .slice(0, 5)
    .map(result => ({
      lat: Number(result.geometry.location.lat),
      lng: Number(result.geometry.location.lng),
      label: result.formatted_address || address
    }))
    .filter(place => Number.isFinite(place.lat) && Number.isFinite(place.lng));
}

async function fetchRoutesDurationSeconds({ origin, destination, departureIso, apiKey }) {
  const body = JSON.stringify({
    origin: { location: { latLng: { latitude: origin.lat, longitude: origin.lng } } },
    destination: { location: { latLng: { latitude: destination.lat, longitude: destination.lng } } },
    travelMode: 'DRIVE',
    routingPreference: 'TRAFFIC_AWARE',
    departureTime: departureIso,
    languageCode: 'en-IN'
  });

  const response = await postJsonWithRetry({
    host: ROUTES_HOST,
    path: ROUTES_PATH,
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(body),
      'X-Goog-Api-Key': apiKey,
      'X-Goog-FieldMask': 'routes.duration'
    },
    body
  });

  const duration = response.routes?.[0]?.duration;
  if (!duration) throw new Error(response.error?.message || 'routes_duration_missing');
  return parseDurationSeconds(duration);
}

function postJsonWithRetry({ host, path, headers, body }, maxRetries = 2) {
  return new Promise((resolve, reject) => {
    let attempt = 0;
    const run = () => {
      const req = https.request({ host, path, method: 'POST', headers }, res => {
        let raw = '';
        res.on('data', chunk => raw += chunk);
        res.on('end', () => {
          let parsed = {};
          try {
            parsed = raw ? JSON.parse(raw) : {};
          } catch (error) {
            reject(error);
            return;
          }
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(parsed);
            return;
          }
          const error = new Error(parsed.error?.message || `routes_http_${res.statusCode}`);
          if (attempt < maxRetries && (res.statusCode >= 500 || res.statusCode === 429)) {
            attempt += 1;
            setTimeout(run, 250 * attempt);
          } else {
            reject(error);
          }
        });
      });
      req.on('error', error => {
        if (attempt < maxRetries) {
          attempt += 1;
          setTimeout(run, 250 * attempt);
        } else {
          reject(error);
        }
      });
      req.write(body);
      req.end();
    };
    run();
  });
}

function getJsonWithRetry({ host, path }, maxRetries = 2) {
  return new Promise((resolve, reject) => {
    let attempt = 0;
    const run = () => {
      https.get({ host, path }, res => {
        let raw = '';
        res.on('data', chunk => raw += chunk);
        res.on('end', () => {
          let parsed = {};
          try {
            parsed = raw ? JSON.parse(raw) : {};
          } catch (error) {
            reject(error);
            return;
          }
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(parsed);
            return;
          }
          const error = new Error(parsed.error_message || `maps_http_${res.statusCode}`);
          if (attempt < maxRetries && (res.statusCode >= 500 || res.statusCode === 429)) {
            attempt += 1;
            setTimeout(run, 250 * attempt);
          } else {
            reject(error);
          }
        });
      }).on('error', error => {
        if (attempt < maxRetries) {
          attempt += 1;
          setTimeout(run, 250 * attempt);
        } else {
          reject(error);
        }
      });
    };
    run();
  });
}

function parseDestination(value) {
  if (!value) return null;
  if (typeof value === 'object' && value.lat && value.lng) {
    return { lat: Number(value.lat), lng: Number(value.lng), label: value.label || `${value.lat},${value.lng}` };
  }
  const text = String(value).trim();
  const match = text.match(/^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*$/);
  if (!match) return null;
  return {
    lat: Number(match[1]),
    lng: Number(match[2]),
    label: text
  };
}

function unavailablePlan(context, destination) {
  const stressBuffer = context.sleepDebtMinutes >= 60 || context.meetingLoadToday >= 5 ? 15 : 8;
  const heuristicEta = heuristicEtaMinutes(context);
  const leaveInMinutes = Math.max(0, context.nextMeetingMinutes - heuristicEta - stressBuffer);
  const shouldAlert = leaveInMinutes <= 30 && Boolean(context.nextMeetingMinutes);
  const destinationLabel = destination || context.nextMeetingLocation || context.destination || null;
  return {
    source: 'maps_unavailable',
    destination: destinationLabel,
    etaMinutes: heuristicEta,
    bufferMinutes: stressBuffer,
    leaveInMinutes,
    leaveByLocal: localTimeFromNow(leaveInMinutes, context.timezone),
    shouldAlert,
    reason: `Maps unavailable; using deterministic ${heuristicEta}m commute estimate plus ${stressBuffer}m stress buffer`,
    modes: [
      {
        id: 'car',
        label: 'Car',
        mode: 'DRIVE',
        available: true,
        distanceText: 'Estimated from CAPE context',
        durationMinutes: heuristicEta,
        durationText: `${heuristicEta} min`,
        leaveInMinutes,
        leaveByLocal: localTimeFromNow(leaveInMinutes, context.timezone),
        arrivalByLocal: localTimeFromNow(Math.max(0, context.nextMeetingMinutes - stressBuffer), context.timezone)
      }
    ],
    directions: [],
    mapsUrl: destinationLabel ? mapsUrl(context.currentLocation, destinationLabel) : null
  };
}

function heuristicEtaMinutes(context) {
  const explicitDelay = Number(context.commuteDelayMinutes ?? 0);
  if (explicitDelay > 0) return Math.max(20, Math.min(90, explicitDelay + 20));
  if (context.locationState === 'commuting') return 40;
  if (context.locationState === 'home') return 45;
  if (context.locationState === 'office') return 25;
  return 35;
}

function normalizeLatLng(value) {
  return {
    lat: Number(value.lat),
    lng: Number(value.lng)
  };
}

function parseDurationSeconds(duration) {
  const match = String(duration).match(/^(\d+(?:\.\d+)?)s$/);
  if (!match) throw new Error(`invalid_routes_duration:${duration}`);
  return Math.ceil(Number(match[1]));
}

function nowEpochSeconds() {
  return Math.floor(Date.now() / 1000);
}

function roundToNearestFiveMinutes(epochSeconds) {
  return Math.round(epochSeconds / STEP_SECONDS) * STEP_SECONDS;
}

function epochToIso(epochSeconds) {
  return new Date(epochSeconds * 1000).toISOString();
}

function mapsUrl(origin, destination) {
  const params = new URLSearchParams({
    api: '1',
    destination
  });
  if (origin?.lat && origin?.lng) params.set('origin', `${origin.lat},${origin.lng}`);
  return `https://www.google.com/maps/dir/?${params.toString()}`;
}

function localTimeFromNow(minutes, timezone = 'Asia/Kolkata') {
  const date = new Date(Date.now() + minutes * 60 * 1000);
  return localTimeFromEpoch(date.getTime(), timezone);
}

function localTimeFromEpoch(epochMs, timezone = 'Asia/Kolkata') {
  const date = new Date(epochMs);
  return date.toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
    timeZone: timezone || 'Asia/Kolkata'
  });
}

module.exports = {
  buildCommutePlan,
  geocodeAddress,
  searchPlaces,
  heuristicEtaMinutes,
  latestDepartureByRoutesApi,
  parseDurationSeconds,
  roundToNearestFiveMinutes
};
