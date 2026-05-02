const { buildDecision } = require('../src');

const decision = buildDecision({
  locationState: 'office',
  sleepDebtMinutes: 95,
  meetingLoadToday: 7,
  commuteDelayMinutes: 22,
  screenTimeLast2hMinutes: 78,
  nextMeetingMinutes: 25,
  permissions: {
    notificationPolicyAccess: true,
    writeSettings: true
  }
});

console.log(JSON.stringify(decision, null, 2));
