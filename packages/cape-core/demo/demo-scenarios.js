const { buildDecision } = require('../src');

const scenarios = {
  office: {
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
  },
  commute: {
    locationState: 'commuting',
    sleepDebtMinutes: 30,
    meetingLoadToday: 3,
    commuteDelayMinutes: 35,
    screenTimeLast2hMinutes: 30,
    nextMeetingMinutes: 80,
    permissions: {}
  },
  recovery: {
    locationState: 'unknown',
    sleepDebtMinutes: 125,
    meetingLoadToday: 5,
    commuteDelayMinutes: 5,
    screenTimeLast2hMinutes: 50,
    nextMeetingMinutes: 180,
    permissions: {
      notificationPolicyAccess: true,
      writeSettings: true
    }
  },
  permissionBlocked: {
    locationState: 'office',
    sleepDebtMinutes: 95,
    meetingLoadToday: 7,
    commuteDelayMinutes: 22,
    screenTimeLast2hMinutes: 78,
    nextMeetingMinutes: 25,
    permissions: {
      notificationPolicyAccess: false,
      writeSettings: false
    }
  }
};

for (const [name, context] of Object.entries(scenarios)) {
  const decision = buildDecision(context);
  console.log(`\n=== ${name} ===`);
  console.log(`type: ${decision.type}`);
  console.log(`pack: ${decision.packId}`);
  console.log(`stress: ${decision.stress.score}/100 ${decision.stress.level}`);
  console.log(`actions: ${decision.actions.join(', ') || 'none'}`);
  console.log(`blocked: ${decision.blockedByPermission.join(', ') || 'none'}`);
  console.log(`why: ${decision.explanation}`);
}
