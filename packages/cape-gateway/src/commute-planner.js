const https = require('https');

async function buildCommutePlan(context) {
  if (!context.nextMeetingMinutes || context.nextMeetingMinutes > 180) return null;

  const destination = context.nextMeetingLocation || context.destination || '';
  const origin = context.currentLocation;
  const fallback = heuristicPlan(context);

  if (!process.env.GOOGLE_MAPS_API_KEY || !destination || !origin?.lat || !origin?.lng) {
    return {
      ...fallback,
      source: 'heuristic',
      destination: destination || null
    };
  }

  try {
    const eta = await fetchGoogleEta(origin, destination);
    const stressBuffer = context.sleepDebtMinutes >= 60 || context.meetingLoadToday >= 5 ? 15 : 8;
    const leaveInMinutes = Math.max(0, context.nextMeetingMinutes - eta - stressBuffer);
    return {
      source: 'google_maps',
      destination,
      etaMinutes: eta,
      bufferMinutes: stressBuffer,
      leaveInMinutes,
      leaveByLocal: localTimeFromNow(leaveInMinutes),
      shouldAlert: leaveInMinutes <= 30,
      reason: `Google Maps ETA ${eta}m plus ${stressBuffer}m buffer`
    };
  } catch (error) {
    return {
      ...fallback,
      source: 'heuristic_fallback',
      destination,
      reason: `${fallback.reason}; Maps unavailable: ${error.message}`
    };
  }
}

function heuristicPlan(context) {
  const baseEta = context.locationState === 'commuting' ? 45 : 30;
  const delay = context.commuteDelayMinutes ?? 0;
  const stressBuffer = context.sleepDebtMinutes >= 60 || context.meetingLoadToday >= 5 ? 15 : 8;
  const etaMinutes = baseEta + delay;
  const leaveInMinutes = Math.max(0, context.nextMeetingMinutes - etaMinutes - stressBuffer);
  return {
    source: 'heuristic',
    etaMinutes,
    bufferMinutes: stressBuffer,
    leaveInMinutes,
    leaveByLocal: localTimeFromNow(leaveInMinutes),
    shouldAlert: leaveInMinutes <= 30,
    reason: `Heuristic ETA ${etaMinutes}m plus ${stressBuffer}m stress buffer`
  };
}

function fetchGoogleEta(origin, destination) {
  const params = new URLSearchParams({
    origins: `${origin.lat},${origin.lng}`,
    destinations: destination,
    departure_time: 'now',
    key: process.env.GOOGLE_MAPS_API_KEY
  });
  const url = `https://maps.googleapis.com/maps/api/distancematrix/json?${params.toString()}`;
  return new Promise((resolve, reject) => {
    https.get(url, response => {
      let raw = '';
      response.on('data', chunk => raw += chunk);
      response.on('end', () => {
        try {
          const json = JSON.parse(raw);
          const element = json.rows?.[0]?.elements?.[0];
          const seconds = element?.duration_in_traffic?.value ?? element?.duration?.value;
          if (!seconds || element.status !== 'OK') reject(new Error(element?.status || json.status || 'maps_error'));
          else resolve(Math.ceil(seconds / 60));
        } catch (error) {
          reject(error);
        }
      });
    }).on('error', reject);
  });
}

function localTimeFromNow(minutes) {
  const date = new Date(Date.now() + minutes * 60 * 1000);
  return date.toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
    timeZone: 'Asia/Kolkata'
  });
}

module.exports = {
  buildCommutePlan
};
