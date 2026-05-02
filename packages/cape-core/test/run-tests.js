const assert = require('assert');
const { calculateStressScore, buildDecision } = require('../src');

const stress = calculateStressScore({
  sleepDebtMinutes: 90,
  meetingLoadToday: 6,
  commuteDelayMinutes: 30,
  screenTimeLast2hMinutes: 80
});

assert.strictEqual(stress.score, 73);
assert.strictEqual(stress.level, 'high');
assert.ok(stress.reasons.includes('sleep_debt'));
assert.ok(stress.reasons.includes('heavy_meeting_load'));

const officeDecision = buildDecision({
  locationState: 'office',
  sleepDebtMinutes: 90,
  meetingLoadToday: 6,
  commuteDelayMinutes: 30,
  screenTimeLast2hMinutes: 80,
  permissions: {
    notificationPolicyAccess: true,
    writeSettings: true
  }
});

assert.strictEqual(officeDecision.type, 'APPLY_PACK');
assert.strictEqual(officeDecision.packId, 'office_focus_high_stress');
assert.deepStrictEqual(officeDecision.actions, ['DND_ON', 'RINGER_VIBRATE', 'BRIGHTNESS_40']);

const permissionDecision = buildDecision({
  locationState: 'office',
  sleepDebtMinutes: 90,
  meetingLoadToday: 6,
  commuteDelayMinutes: 30,
  screenTimeLast2hMinutes: 80,
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
  permissions: {}
});

assert.strictEqual(commuteDecision.type, 'APPLY_PACK');
assert.strictEqual(commuteDecision.packId, 'commute_alert');
assert.deepStrictEqual(commuteDecision.actions, ['SEND_DEPARTURE_ALERT']);

console.log('All CAPE core tests passed');
