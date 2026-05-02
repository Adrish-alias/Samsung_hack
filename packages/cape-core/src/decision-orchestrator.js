const { calculateStressScore } = require('./stress-score');
const { selectBehaviorPack } = require('./pack-selector');

function buildDecision(context) {
  const stress = calculateStressScore(context);
  const pack = selectBehaviorPack(context, stress);
  const requiredPermissions = permissionsFor(pack.actions);
  const blockedByPermission = requiredPermissions.filter(permission => {
    const granted = context.permissions?.[permission];
    return granted === false;
  });

  const commutePlan = context.commutePlan ?? null;
  const actions = [...pack.actions];
  if (commutePlan?.shouldAlert && !actions.includes('SEND_DEPARTURE_ALERT')) {
    actions.push('SEND_DEPARTURE_ALERT');
  }

  return {
    type: blockedByPermission.length > 0 ? 'REQUEST_PERMISSION' : actions.length > 0 ? 'APPLY_PACK' : 'OBSERVE',
    packId: pack.id,
    confidence: pack.confidence,
    stress,
    actions: blockedByPermission.length > 0 ? [] : actions,
    blockedByPermission,
    explanation: explain(context, stress, pack, blockedByPermission, commutePlan),
    commutePlan,
    reasoningNote: context.reasoningNote ?? null
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

function explain(context, stress, pack, blockedByPermission, commutePlan) {
  if (blockedByPermission.length > 0) {
    return `CAPE needs ${blockedByPermission.join(', ')} before applying ${pack.id}.`;
  }

  if (commutePlan?.shouldAlert) {
    return `Leave by ${commutePlan.leaveByLocal}; ETA is ${commutePlan.etaMinutes} min with ${commutePlan.bufferMinutes} min buffer.`;
  }

  if (pack.id === 'commute_alert') {
    return `Upcoming meeting and commute pressure detected; CAPE should send a departure alert.`;
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

  return `No confident automation needed; CAPE should continue observing.`;
}

module.exports = {
  buildDecision
};
