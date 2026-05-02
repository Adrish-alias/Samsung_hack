function selectBehaviorPack(context, stress) {
  const location = context.locationState ?? 'unknown';
  const minutesToMeeting = context.nextMeetingMinutes ?? null;

  if (location === 'commuting' && minutesToMeeting !== null && minutesToMeeting <= 120) {
    return {
      id: 'commute_alert',
      confidence: 0.86,
      actions: ['SEND_DEPARTURE_ALERT']
    };
  }

  if (location === 'office' && stress.score >= 60) {
    return {
      id: 'office_focus_high_stress',
      confidence: 0.88,
      actions: ['DND_ON', 'RINGER_VIBRATE', 'BRIGHTNESS_40']
    };
  }

  if (stress.score >= 60 && stress.reasons.includes('sleep_debt')) {
    return {
      id: 'recovery_mode',
      confidence: 0.82,
      actions: ['SOFT_NOTIFICATIONS', 'BREAK_REMINDER', 'BRIGHTNESS_65']
    };
  }

  if (location === 'home') {
    return {
      id: 'home_evening',
      confidence: 0.74,
      actions: ['DND_OFF', 'RINGER_NORMAL', 'BRIGHTNESS_AUTO']
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
