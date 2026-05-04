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

  if (commutePlan?.shouldAlert && (context.locationState === 'commuting' || (context.nextMeetingMinutes ?? 999) <= 60)) {
    return {
      id: 'commute_alert',
      confidence: 0.82 + Math.min(0.1, (stress.score / 100) * 0.08),
      actions: ['SEND_DEPARTURE_ALERT']
    };
  }

  if (context.locationState === 'office' && stress.score >= 60 && !context.isWeekend) {
    return {
      id: 'office_focus_high_stress',
      confidence: 0.84 + Math.min(0.08, locationConfidence * 0.08) - Math.min(0.12, rejectionBias * 0.12),
      actions: ['DND_ON', 'RINGER_VIBRATE', 'BRIGHTNESS_40']
    };
  }

  if (stress.score >= 60 && stress.reasons.includes('sleep_debt')) {
    return {
      id: 'recovery_mode',
      confidence: 0.79 + (stress.reasons.includes('heavy_meeting_load') ? 0.04 : 0),
      actions: ['SOFT_NOTIFICATIONS', 'BREAK_REMINDER', 'BRIGHTNESS_65']
    };
  }

  if (context.locationState === 'home' && (context.hourOfDay ?? 0) >= 18) {
    return {
      id: 'home_evening',
      confidence: 0.74,
      actions: ['DND_OFF', 'RINGER_NORMAL', 'BRIGHTNESS_AUTO']
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
    if (action === 'DND_ON' || action === 'SOFT_NOTIFICATIONS') permissions.add('notificationPolicyAccess');
    if (action.startsWith('BRIGHTNESS')) permissions.add('writeSettings');
  }
  return [...permissions];
}

function explain(pack, stress, commutePlan) {
  if (commutePlan?.shouldAlert) {
    return `Leave by ${commutePlan.leaveByLocal}; ETA is ${commutePlan.etaMinutes} min with ${commutePlan.bufferMinutes} min buffer.`;
  }

  if (pack.id === 'office_focus_high_stress') {
    return `Office context with ${stress.level} stress (${stress.score}/100); CAPE should reduce interruptions.`;
  }

  if (pack.id === 'recovery_mode') {
    return `Sleep debt is raising stress to ${stress.score}/100; CAPE should use low-intrusion recovery behavior.`;
  }

  if (pack.id === 'home_evening') {
    return `Home context detected; CAPE should restore normal phone behavior.`;
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
