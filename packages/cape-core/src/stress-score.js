const clamp = (value, min, max) => Math.max(min, Math.min(max, value));

function normalizeSleepDebt(minutes) {
  return clamp(minutes / 120, 0, 1);
}

function normalizeMeetingLoad(count) {
  return clamp(count / 8, 0, 1);
}

function normalizeCommutePressure(delayMinutes) {
  return clamp(delayMinutes / 45, 0, 1);
}

function normalizeUsageIntensity(minutesLastTwoHours) {
  return clamp(minutesLastTwoHours / 120, 0, 1);
}

function levelFor(score) {
  if (score >= 75) return 'critical';
  if (score >= 60) return 'high';
  if (score >= 35) return 'medium';
  return 'low';
}

function calculateStressScore(context) {
  const sleep = normalizeSleepDebt(context.sleepDebtMinutes ?? 0);
  const meetings = normalizeMeetingLoad(context.meetingLoadToday ?? 0);
  const commute = normalizeCommutePressure(context.commuteDelayMinutes ?? 0);
  const usage = normalizeUsageIntensity(context.screenTimeLast2hMinutes ?? 0);

  const weighted = (0.4 * sleep) + (0.3 * meetings) + (0.2 * commute) + (0.1 * usage);
  const score = Math.round(weighted * 100);

  const reasons = [];
  if (sleep >= 0.5) reasons.push('sleep_debt');
  if (meetings >= 0.5) reasons.push('heavy_meeting_load');
  if (commute >= 0.45) reasons.push('commute_pressure');
  if (usage >= 0.65) reasons.push('high_usage_intensity');

  return {
    score,
    level: levelFor(score),
    reasons,
    components: {
      sleep: Math.round(sleep * 100),
      meetings: Math.round(meetings * 100),
      commute: Math.round(commute * 100),
      usage: Math.round(usage * 100)
    }
  };
}

module.exports = {
  calculateStressScore
};
