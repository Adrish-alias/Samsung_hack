function selectBehaviorPack(context, stress) {
  const location = context.locationState ?? 'unknown';
  const minutesToMeeting = context.nextMeetingMinutes ?? null;
  const appSwitches = Number(context.appSwitchCountLast30Min ?? context.taskFragmentationScore ?? 0);
  const unlocks = Number(context.screenUnlockCountLast30Min ?? context.focusDropScore ?? 0);

  if (location === 'commuting') {
    return {
      id: 'commute_alert',
      confidence: 0.92,
      actions: ['DND_OFF', 'RINGER_NORMAL', 'BRIGHTNESS_65', 'WALLPAPER_COMMUTE', 'SEND_DEPARTURE_ALERT']
    };
  }

  if (location === 'office' || location === 'college' || (minutesToMeeting !== null && minutesToMeeting <= 10)) {
    return {
      id: 'office_focus_high_stress',
      confidence: 0.9,
      actions: ['DND_ON', 'RINGER_VIBRATE', 'BRIGHTNESS_40', 'WALLPAPER_FOCUS']
    };
  }

  if (stress.score >= 60 && stress.reasons.includes('sleep_debt')) {
    return {
      id: 'recovery_mode',
      confidence: 0.72,
      actions: ['DND_OFF', 'RINGER_VIBRATE', 'SOFT_NOTIFICATIONS', 'BREAK_REMINDER', 'BRIGHTNESS_50', 'WALLPAPER_RELAX']
    };
  }

  if (location === 'home' || location === 'relaxing') {
    return {
      id: 'home_evening',
      confidence: 0.88,
      actions: ['DND_OFF', 'RINGER_NORMAL', 'BRIGHTNESS_AUTO', 'WALLPAPER_RELAX']
    };
  }

  if (appSwitches >= 15) {
    return {
      id: 'recovery_mode',
      confidence: 0.83,
      actions: ['DND_OFF', 'RINGER_VIBRATE', 'SOFT_NOTIFICATIONS', 'BREAK_REMINDER', 'BRIGHTNESS_50', 'WALLPAPER_RELAX']
    };
  }

  if (appSwitches <= 3 && unlocks <= 2 && Number(context.screenTimeLast2hMinutes ?? 0) >= 45) {
    return {
      id: 'office_focus_high_stress',
      confidence: 0.82,
      actions: ['DND_ON', 'BRIGHTNESS_50', 'WALLPAPER_FOCUS']
    };
  }

  return {
    id: 'observe_only',
    confidence: 0.65,
    actions: []
  };
}

module.exports = {
  selectBehaviorPack
};
