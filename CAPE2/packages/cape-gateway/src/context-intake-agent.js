function createContextIntakeAgent(options = {}) {
  const now = options.now ?? (() => new Date());

  function normalize(rawContext = {}) {
    const timezone = asText(rawContext.timezone) || 'Asia/Kolkata';
    const currentTimeIso = safeIso(rawContext.currentTimeIso, now);
    const clock = buildClock(currentTimeIso, timezone, rawContext.dayOfWeek, rawContext.hourOfDay);
    const permissions = normalizePermissions(rawContext);
    const currentLocation = normalizeLocation(rawContext);
    const nextMeetingMinutes = asOptionalInt(rawContext.nextMeetingMinutes, 0, 24 * 60);

    return {
      source: asText(rawContext.source) || 'android-apk',
      locationState: normalizeLocationState(rawContext.locationState),
      sleepDebtMinutes: asInt(rawContext.sleepDebtMinutes, 0, 12 * 60),
      meetingLoadToday: asInt(rawContext.meetingLoadToday, 0, 24),
      commuteDelayMinutes: asInt(rawContext.commuteDelayMinutes, 0, 4 * 60),
      screenTimeLast2hMinutes: asInt(rawContext.screenTimeLast2hMinutes, 0, 4 * 60),
      appSwitchCountLast30Min: asInt(rawContext.appSwitchCountLast30Min, 0, 200),
      screenUnlockCountLast30Min: asInt(rawContext.screenUnlockCountLast30Min, 0, 200),
      notificationCountLast30Min: asInt(rawContext.notificationCountLast30Min, 0, 200),
      foregroundAppCategory: normalizeAppCategory(rawContext.foregroundAppCategory),
      timeAtLocationMinutes: asInt(rawContext.timeAtLocationMinutes, 0, 24 * 60),
      implicitWorkload: normalizeWorkload(rawContext.implicitWorkload),
      todoPendingCount: asInt(rawContext.todoPendingCount, 0, 200),
      todoUrgentCount: asInt(rawContext.todoUrgentCount, 0, 200),
      todoOverdueCount: asInt(rawContext.todoOverdueCount, 0, 200),
      todoPressureScore: asInt(rawContext.todoPressureScore, 0, 100),
      learnedTodoUpdateHours: normalizeHourList(rawContext.learnedTodoUpdateHours),
      taskFragmentationScore: asInt(rawContext.taskFragmentationScore ?? rawContext.appSwitchCountLast30Min, 0, 200),
      focusDropScore: asInt(rawContext.focusDropScore ?? rawContext.screenUnlockCountLast30Min, 0, 200),
      nextMeetingMinutes,
      nextMeetingStartEpochMs: asOptionalInt(rawContext.nextMeetingStartEpochMs, 0, Number.MAX_SAFE_INTEGER),
      nextMeetingEndEpochMs: asOptionalInt(rawContext.nextMeetingEndEpochMs, 0, Number.MAX_SAFE_INTEGER),
      nextMeetingLocation: asNullableText(rawContext.nextMeetingLocation ?? rawContext.destination),
      nextMeetingTitle: asNullableText(rawContext.nextMeetingTitle),
      currentLocation,
      permissions,
      notificationPolicyAccess: permissions.notificationPolicyAccess,
      writeSettings: permissions.writeSettings,
      timezone,
      currentTimeIso,
      dayOfWeek: clock.dayOfWeek,
      hourOfDay: clock.hourOfDay,
      minuteOfHour: clock.minuteOfHour,
      isWeekend: clock.isWeekend,
      timeSegment: clock.timeSegment,
      rawSignalCount: countDefinedSignals(rawContext)
    };
  }

  return {
    normalize
  };
}

function normalizeHourList(value) {
  if (!Array.isArray(value)) return [];
  return value.map(hour => asOptionalInt(hour, 0, 23)).filter(hour => hour != null).slice(0, 6);
}

function normalizePermissions(rawContext) {
  const permissions = rawContext.permissions ?? {};
  return {
    notificationPolicyAccess: asBoolean(permissions.notificationPolicyAccess ?? rawContext.notificationPolicyAccess),
    writeSettings: asBoolean(permissions.writeSettings ?? rawContext.writeSettings),
    notifications: asBoolean(permissions.notifications ?? rawContext.notificationsPermission),
    calendar: asBoolean(permissions.calendar ?? rawContext.calendarPermission),
    location: asBoolean(permissions.location ?? rawContext.locationPermission),
    usageStats: asBoolean(permissions.usageStats ?? rawContext.usageStatsPermission)
  };
}

function normalizeLocation(rawContext) {
  const currentLocation = rawContext.currentLocation ?? {};
  const lat = asOptionalNumber(currentLocation.lat ?? rawContext.currentLatitude);
  const lng = asOptionalNumber(currentLocation.lng ?? rawContext.currentLongitude);
  if (lat == null || lng == null) return null;
  return { lat, lng };
}

function buildClock(currentTimeIso, timezone, rawDayOfWeek, rawHourOfDay) {
  const currentDate = new Date(currentTimeIso);
  const dayOfWeek = asText(rawDayOfWeek) || currentDate.toLocaleDateString('en-US', {
    weekday: 'long',
    timeZone: timezone
  }).toLowerCase();
  const hourOfDay = asOptionalInt(rawHourOfDay, 0, 23) ?? Number(currentDate.toLocaleTimeString('en-US', {
    hour: '2-digit',
    hour12: false,
    timeZone: timezone
  }));
  const minuteOfHour = Number(currentDate.toLocaleTimeString('en-US', {
    minute: '2-digit',
    hour12: false,
    timeZone: timezone
  }));

  return {
    dayOfWeek,
    hourOfDay,
    minuteOfHour,
    isWeekend: dayOfWeek === 'saturday' || dayOfWeek === 'sunday',
    timeSegment: segmentForHour(hourOfDay)
  };
}

function segmentForHour(hourOfDay) {
  if (hourOfDay < 6) return 'late_night';
  if (hourOfDay < 12) return 'morning';
  if (hourOfDay < 17) return 'afternoon';
  if (hourOfDay < 22) return 'evening';
  return 'night';
}

function normalizeLocationState(value) {
  const normalized = asText(value)?.toLowerCase();
  if (['office', 'college', 'home', 'relaxing', 'commuting'].includes(normalized)) return normalized;
  return 'unknown';
}

function normalizeAppCategory(value) {
  const normalized = asText(value)?.toLowerCase();
  if (['work', 'social', 'entertainment', 'mixed'].includes(normalized)) return normalized;
  return 'mixed';
}

function normalizeWorkload(value) {
  const normalized = asText(value)?.toUpperCase();
  if (['LOW', 'MEDIUM', 'HIGH'].includes(normalized)) return normalized;
  return 'LOW';
}

function countDefinedSignals(rawContext) {
  return Object.values(rawContext).filter(value => value !== null && value !== undefined && value !== '').length;
}

function asBoolean(value) {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'string') return value.toLowerCase() === 'true';
  if (typeof value === 'number') return value > 0;
  return false;
}

function asInt(value, min, max) {
  return clamp(Number.parseInt(value ?? 0, 10) || 0, min, max);
}

function asOptionalInt(value, min, max) {
  if (value === null || value === undefined || value === '') return null;
  return clamp(Number.parseInt(value, 10) || 0, min, max);
}

function asOptionalNumber(value) {
  if (value === null || value === undefined || value === '') return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function asText(value) {
  if (value === null || value === undefined) return null;
  const text = String(value).trim();
  return text ? text : null;
}

function asNullableText(value) {
  return asText(value);
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function safeIso(value, now) {
  const text = asText(value);
  if (!text) return now().toISOString();
  const parsed = new Date(text);
  return Number.isNaN(parsed.getTime()) ? now().toISOString() : text;
}

module.exports = {
  createContextIntakeAgent
};
