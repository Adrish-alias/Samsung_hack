function createDecisionOrchestratorAgent() {
  function decide({ context, stress, commutePlan, reasoningNote = null }) {
    const pack = selectPack(context, stress, commutePlan);
    const actions = [...pack.actions];
    if (commutePlan?.shouldAlert && !actions.includes('SEND_DEPARTURE_ALERT')) {
      actions.push('SEND_DEPARTURE_ALERT');
    }
    const blockedByPermission = permissionsFor(actions).filter(permission => context.permissions?.[permission] === false);

    return {
      type: blockedByPermission.length > 0 ? 'REQUEST_PERMISSION' : actions.length > 0 ? 'APPLY_PACK' : 'OBSERVE',
      packId: pack.id,
      confidence: clamp(pack.confidence, 0.0, 0.99),
      stress,
      actions: blockedByPermission.length > 0 ? [] : actions,
      suggestedActions: [],
      blockedByPermission,
      explanation: explain(pack, stress, commutePlan),
      commutePlan,
      reasoningNote
    };
  }

  return {
    decide
  };
}

function selectPack(context, stress, commutePlan) {
  const locationConfidence = Number(context.memory?.routine?.locations?.[context.locationState]?.confidence ?? 0);
  const rejectionBias = Number(context.memory?.rejectionBias?.office_focus_high_stress ?? 0);
  const fragmentation = Number(context.taskFragmentationScore ?? context.appSwitchCountLast30Min ?? 0);
  const unlocks = Number(context.focusDropScore ?? context.screenUnlockCountLast30Min ?? 0);
  const stableFocus = fragmentation <= 3 && unlocks <= 2 && Number(context.screenTimeLast2hMinutes ?? 0) >= 45;
  const meetingStarting = context.nextMeetingMinutes != null && context.nextMeetingMinutes <= 10;

  if (context.locationState === 'commuting' || (commutePlan?.shouldAlert && (context.nextMeetingMinutes ?? 999) <= 60)) {
    return {
      id: 'commute_alert',
      confidence: 0.9 + Math.min(0.05, (stress.score / 100) * 0.04),
      actions: ['DND_OFF', 'RINGER_NORMAL', 'BRIGHTNESS_65', 'WALLPAPER_COMMUTE', 'SEND_DEPARTURE_ALERT']
    };
  }

  if (((context.locationState === 'office' || context.locationState === 'college') && !context.isWeekend) || meetingStarting) {
    return {
      id: 'office_focus_high_stress',
      confidence: 0.84 + Math.min(0.08, locationConfidence * 0.08) - Math.min(0.12, rejectionBias * 0.12),
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

  if (context.locationState === 'home' || context.locationState === 'relaxing') {
    return {
      id: 'home_evening',
      confidence: 0.88,
      actions: ['DND_OFF', 'RINGER_NORMAL', 'BRIGHTNESS_AUTO', 'WALLPAPER_RELAX']
    };
  }

  if (fragmentation >= 15) {
    return {
      id: 'recovery_mode',
      confidence: 0.83,
      actions: ['DND_OFF', 'RINGER_VIBRATE', 'SOFT_NOTIFICATIONS', 'BREAK_REMINDER', 'BRIGHTNESS_50', 'WALLPAPER_RELAX']
    };
  }

  if (stableFocus) {
    return {
      id: 'office_focus_high_stress',
      confidence: 0.82,
      actions: ['DND_ON', 'BRIGHTNESS_50', 'WALLPAPER_FOCUS']
    };
  }

  return {
    id: 'observe_only',
    confidence: 0.62,
    actions: []
  };
}

function permissionsFor(actions) {
  const permissions = new Set();
  for (const action of actions) {
    if (action === 'DND_ON' || action === 'DND_OFF' || action === 'SOFT_NOTIFICATIONS') permissions.add('notificationPolicyAccess');
    if (action.startsWith('BRIGHTNESS')) permissions.add('writeSettings');
    if (['SEND_DEPARTURE_ALERT', 'SOFT_NOTIFICATIONS', 'BREAK_REMINDER'].includes(action)) permissions.add('notifications');
  }
  return [...permissions];
}

function explain(pack, stress, commutePlan) {
  if (commutePlan?.shouldAlert) {
    const routeHint = commutePlan.modes?.length
      ? ` Options: ${commutePlan.modes.map(mode => `${mode.label} ${mode.durationText}`).join(', ')}.`
      : '';
    return `Leave by ${commutePlan.leaveByLocal}; ETA is ${commutePlan.etaMinutes} min with ${commutePlan.bufferMinutes} min buffer.${routeHint}`;
  }

  if (pack.id === 'office_focus_high_stress') {
    return `Focus location detected with ${stress.level} stress (${stress.score}/100); CAPE should reduce interruptions.`;
  }

  if (pack.id === 'recovery_mode') {
    return `Sleep debt is raising stress to ${stress.score}/100; CAPE should use low-intrusion recovery behavior.`;
  }

  if (pack.id === 'home_evening') {
    return `Home or relaxing context detected; CAPE should restore calmer phone behavior.`;
  }

  if (pack.id === 'commute_alert') {
    return `Upcoming meeting and commute pressure detected; CAPE should send a departure alert.`;
  }

  return 'No confident automation needed; CAPE should continue observing.';
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

module.exports = {
  createDecisionOrchestratorAgent
};
