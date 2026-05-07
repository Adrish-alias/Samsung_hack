const assert = require('assert');
const { calculateStressScore, buildDecision } = require('../src');

const stress = calculateStressScore({
  sleepDebtMinutes: 90,
  meetingLoadToday: 6,
  commuteDelayMinutes: 30,
  screenTimeLast2hMinutes: 80,
  appSwitchCountLast30Min: 18,
  screenUnlockCountLast30Min: 8,
  notificationCountLast30Min: 12,
  foregroundAppCategory: 'mixed',
  implicitWorkload: 'HIGH'
});

assert.strictEqual(stress.score, 82);
assert.strictEqual(stress.level, 'critical');
assert.ok(stress.reasons.includes('sleep_debt'));
assert.ok(stress.reasons.includes('task_fragmentation'));
assert.ok(stress.reasons.includes('implicit_workload'));

const officeDecision = buildDecision({
  locationState: 'office',
  sleepDebtMinutes: 90,
  meetingLoadToday: 6,
  commuteDelayMinutes: 30,
  screenTimeLast2hMinutes: 80,
  appSwitchCountLast30Min: 18,
  screenUnlockCountLast30Min: 8,
  notificationCountLast30Min: 12,
  permissions: {
    notificationPolicyAccess: true,
    writeSettings: true
  }
});

assert.strictEqual(officeDecision.type, 'APPLY_PACK');
assert.strictEqual(officeDecision.packId, 'office_focus_high_stress');
assert.deepStrictEqual(officeDecision.actions, ['DND_ON', 'RINGER_VIBRATE', 'BRIGHTNESS_40', 'WALLPAPER_FOCUS']);

const permissionDecision = buildDecision({
  locationState: 'office',
  sleepDebtMinutes: 90,
  meetingLoadToday: 6,
  commuteDelayMinutes: 30,
  screenTimeLast2hMinutes: 80,
  appSwitchCountLast30Min: 18,
  screenUnlockCountLast30Min: 8,
  notificationCountLast30Min: 12,
  permissions: {
    notificationPolicyAccess: false,
    writeSettings: true
  }
});

assert.strictEqual(permissionDecision.type, 'REQUEST_PERMISSION');
assert.deepStrictEqual(permissionDecision.blockedByPermission, ['notificationPolicyAccess']);

const commuteDecision = buildDecision({
  locationState: 'commuting',
  nextMeetingMinutes: 75,
  sleepDebtMinutes: 20,
  meetingLoadToday: 2,
  commuteDelayMinutes: 25,
  screenTimeLast2hMinutes: 20,
  permissions: {
    notificationPolicyAccess: true,
    writeSettings: true,
    notifications: true
  }
});

assert.strictEqual(commuteDecision.type, 'APPLY_PACK');
assert.strictEqual(commuteDecision.packId, 'commute_alert');
assert.deepStrictEqual(commuteDecision.actions, ['DND_OFF', 'RINGER_NORMAL', 'BRIGHTNESS_65', 'WALLPAPER_COMMUTE', 'SEND_DEPARTURE_ALERT']);

console.log('All CAPE core tests passed');
