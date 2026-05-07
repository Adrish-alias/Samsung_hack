const { buildCommutePlan } = require('./commute-planner');

function createCommuteAgent(options = {}) {
  const planBuilder = options.planBuilder ?? buildCommutePlan;

  async function plan(context) {
    const basePlan = await planBuilder(context);
    if (!basePlan) return null;

    const history = findPattern(
      context.memory?.routine?.routines?.commute_patterns,
      context.nextMeetingLocation
    );
    if (!history?.avg_eta_minutes || basePlan.source === 'google_maps') {
      return basePlan;
    }

    const etaMinutes = Math.max(basePlan.etaMinutes, Number(history.avg_eta_minutes));
    if (etaMinutes === basePlan.etaMinutes) return basePlan;

    const leaveInMinutes = Math.max(0, (context.nextMeetingMinutes ?? 0) - etaMinutes - basePlan.bufferMinutes);
    return {
      ...basePlan,
      source: `${basePlan.source}+memory`,
      etaMinutes,
      leaveInMinutes,
      leaveByLocal: localTimeFromNow(leaveInMinutes, context.timezone),
      shouldAlert: leaveInMinutes <= 30,
      reason: `${basePlan.reason}; adjusted to historical ETA ${history.avg_eta_minutes}m`
    };
  }

  return {
    plan
  };
}

function findPattern(patterns, destination) {
  if (!Array.isArray(patterns) || !destination) return null;
  const normalizedDestination = String(destination).trim().toLowerCase();
  return patterns.find(pattern =>
    String(pattern.destination ?? '').trim().toLowerCase() === normalizedDestination
  ) ?? null;
}

function localTimeFromNow(minutes, timezone = 'Asia/Kolkata') {
  const date = new Date(Date.now() + minutes * 60 * 1000);
  return date.toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
    timeZone: timezone
  });
}

module.exports = {
  createCommuteAgent
};
