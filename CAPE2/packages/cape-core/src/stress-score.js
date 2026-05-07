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

function normalizeAppSwitches(count) {
  return clamp(count / 30, 0, 1);
}

function normalizeUnlocks(count) {
  return clamp(count / 12, 0, 1);
}

function normalizeNotifications(count) {
  return clamp(count / 20, 0, 1);
}

function normalizeTodoPressure(score) {
  return clamp(score / 100, 0, 1);
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
  const appSwitches = normalizeAppSwitches(context.appSwitchCountLast30Min ?? context.taskFragmentationScore ?? 0);
  const unlocks = normalizeUnlocks(context.screenUnlockCountLast30Min ?? context.focusDropScore ?? 0);
  const notifications = normalizeNotifications(context.notificationCountLast30Min ?? 0);
  const todo = normalizeTodoPressure(context.todoPressureScore ?? 0);

  const weighted = (0.25 * sleep) +
    (0.14 * appSwitches) +
    (0.10 * unlocks) +
    (0.15 * usage) +
    (0.10 * notifications) +
    (0.10 * commute) +
    (0.08 * meetings) +
    (0.08 * todo);
  const workloadBoost = context.implicitWorkload === 'HIGH' ? 10 : 0;
  const mixedBoost = context.foregroundAppCategory === 'mixed' ? 5 : 0;
  const memoryAdjustment = Number(context.memory?.profile?.stress_triggers?.adjustment ?? 0);
  const score = Math.round(clamp((weighted * 100) + workloadBoost + mixedBoost + memoryAdjustment, 0, 100));

  const reasons = [];
  if (sleep >= 0.5) reasons.push('sleep_debt');
  if (appSwitches >= 0.5) reasons.push('task_fragmentation');
  if (unlocks >= 0.5) reasons.push('focus_drops');
  if (notifications >= 0.5) reasons.push('notification_pressure');
  if (commute >= 0.45) reasons.push('commute_pressure');
  if (usage >= 0.65) reasons.push('high_usage_intensity');
  if (meetings >= 0.5) reasons.push('meeting_load');
  if (todo >= 0.45) reasons.push('todo_pressure');
  if (context.implicitWorkload === 'HIGH') reasons.push('implicit_workload');
  if (context.foregroundAppCategory === 'mixed') reasons.push('mixed_app_context');

  return {
    score,
    level: levelFor(score),
    reasons,
    components: {
      sleep: Math.round(sleep * 100),
      appSwitches: Math.round(appSwitches * 100),
      unlocks: Math.round(unlocks * 100),
      notifications: Math.round(notifications * 100),
      meetings: Math.round(meetings * 100),
      commute: Math.round(commute * 100),
      usage: Math.round(usage * 100),
      todo: Math.round(todo * 100)
    }
  };
}

module.exports = {
  calculateStressScore
};
