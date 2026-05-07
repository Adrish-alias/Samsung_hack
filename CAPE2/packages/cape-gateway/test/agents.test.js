const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { createContextIntakeAgent } = require('../src/context-intake-agent');
const { createStressScoringAgent } = require('../src/stress-scoring-agent');
const { createCommuteAgent } = require('../src/commute-agent');
const { createDecisionOrchestratorAgent } = require('../src/decision-orchestrator-agent');
const { createYamlMemoryStore } = require('../src/yaml-memory');
const { createRoutineMemoryAgent } = require('../src/routine-memory-agent');
const { createSafetyPermissionAgent } = require('../src/safety-permission-agent');
const { createFeedbackLearningAgent } = require('../src/feedback-learning-agent');

async function runAgentModuleTests() {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'cape-memory-'));
  const store = createYamlMemoryStore(path.join(tempDir, 'memory'));
  store.ensure();

  const intake = createContextIntakeAgent({
    now: () => new Date('2026-05-04T09:15:00+05:30')
  });
  const routineMemory = createRoutineMemoryAgent(store);
  const stressAgent = createStressScoringAgent();
  const commuteAgent = createCommuteAgent({
    planBuilder: async () => ({
      source: 'heuristic',
      etaMinutes: 35,
      bufferMinutes: 15,
      leaveInMinutes: 10,
      leaveByLocal: '09:25 AM',
      shouldAlert: true,
      reason: 'heuristic test'
    })
  });
  const orchestrator = createDecisionOrchestratorAgent();
  const safety = createSafetyPermissionAgent();
  const feedback = createFeedbackLearningAgent(store);

  const normalized = intake.normalize({
    locationState: 'office',
    nextMeetingTitle: 'Weekly 1:1',
    nextMeetingMinutes: 25,
    nextMeetingLocation: 'Samsung Office',
    sleepDebtMinutes: 90,
    meetingLoadToday: 6,
    commuteDelayMinutes: 15,
    screenTimeLast2hMinutes: 45,
    appSwitchCountLast30Min: 18,
    screenUnlockCountLast30Min: 8,
    notificationCountLast30Min: 10,
    foregroundAppCategory: 'mixed',
    implicitWorkload: 'HIGH',
    permissions: {
      notificationPolicyAccess: true,
      writeSettings: true,
      notifications: true,
      calendar: true,
      location: true
    }
  });

  assert.strictEqual(normalized.dayOfWeek, 'monday');
  assert.strictEqual(
    intake.normalize({ currentTimeIso: 'not-a-date' }).currentTimeIso,
    '2026-05-04T03:45:00.000Z'
  );
  const hydrated = routineMemory.hydrateContext(normalized);
  assert.strictEqual(hydrated.memory.minimumConfidenceToApply, 0.78);

  const stress = stressAgent.score(hydrated);
  assert.strictEqual(stress.level, 'high');
  assert.ok(stress.reasons.includes('todo_pressure') || normalized.todoPressureScore === 0);

  const calm = stressAgent.score({ sleepDebtMinutes: 0, meetingLoadToday: 0, commuteDelayMinutes: 0, screenTimeLast2hMinutes: 20, appSwitchCountLast30Min: 2, screenUnlockCountLast30Min: 1, notificationCountLast30Min: 1, foregroundAppCategory: 'work', implicitWorkload: 'LOW', todoPressureScore: 0 });
  const moderate = stressAgent.score({ sleepDebtMinutes: 45, meetingLoadToday: 3, commuteDelayMinutes: 0, screenTimeLast2hMinutes: 65, appSwitchCountLast30Min: 10, screenUnlockCountLast30Min: 4, notificationCountLast30Min: 6, foregroundAppCategory: 'mixed', implicitWorkload: 'MEDIUM', todoPressureScore: 35 });
  const highRaw = stressAgent.score({ sleepDebtMinutes: 120, meetingLoadToday: 8, commuteDelayMinutes: 40, screenTimeLast2hMinutes: 120, appSwitchCountLast30Min: 30, screenUnlockCountLast30Min: 12, notificationCountLast30Min: 20, foregroundAppCategory: 'mixed', implicitWorkload: 'HIGH', todoPressureScore: 90 });
  const smoothed = stressAgent.score({
    sleepDebtMinutes: 120,
    meetingLoadToday: 8,
    commuteDelayMinutes: 40,
    screenTimeLast2hMinutes: 120,
    appSwitchCountLast30Min: 30,
    screenUnlockCountLast30Min: 12,
    notificationCountLast30Min: 20,
    foregroundAppCategory: 'mixed',
    implicitWorkload: 'HIGH',
    todoPressureScore: 90,
    memory: { routine: { runtime_state: { last_stress_score: calm.score } } }
  });
  assert.ok(calm.score < moderate.score);
  assert.ok(moderate.score < highRaw.score);
  assert.ok(smoothed.score < highRaw.score);
  assert.ok(smoothed.reasons.includes('stress_smoothing'));

  const commutePlan = await commuteAgent.plan({ ...hydrated, stress });
  const preview = orchestrator.decide({ context: hydrated, stress, commutePlan });
  assert.strictEqual(preview.packId, 'commute_alert');

  routineMemory.rememberObservation(hydrated, stress, commutePlan, preview);
  const memoryAfterObservation = store.readAll();
  assert.strictEqual(memoryAfterObservation.routine.routines.commute_patterns[0].destination, 'Samsung Office');
  assert.strictEqual(memoryAfterObservation.routine.routines.commute_patterns[0].avg_eta_minutes, 35);

  let decision = safety.evaluate(hydrated, {
    ...preview,
    packId: 'office_focus_high_stress',
    confidence: 0.7,
    stress: { score: 70, level: 'high' },
    actions: ['DND_ON'],
    suggestedActions: [],
    blockedByPermission: [],
    explanation: 'test'
  });

  assert.strictEqual(decision.type, 'SUGGEST_PACK');
  assert.deepStrictEqual(decision.suggestedActions, ['DND_ON']);

  const learned = feedback.record({
    packId: 'office_focus_high_stress',
    signal: 'rejected',
    note: "don't do this during 1:1s on fridays"
  });

  assert.strictEqual(learned.ok, true);
  assert.ok(learned.updated.learned.includes('meeting keyword override 1:1s'));
  assert.ok(learned.updated.learned.includes('weekday override friday'));

  const approval = feedback.record({
    type: 'decision_approval',
    packId: 'commute_alert',
    signal: 'accepted',
    note: 'User approved commute pack',
    actions: ['SEND_DEPARTURE_ALERT'],
    confidence: 0.92,
    timestamp: '2026-05-04T09:20:00+05:30'
  });
  assert.strictEqual(approval.updated.signal, 'accepted');

  const todoLearning = feedback.record({
    type: 'todo_update',
    pending: 3,
    urgent: 1,
    overdue: 1,
    note: 'Morning todo update',
    timestamp: '2026-05-04T09:25:00+05:30'
  });
  assert.ok(todoLearning.updated.learned.includes('todo update window 03:00') || todoLearning.updated.learned.some(item => item.startsWith('todo update window')));

  const reflection = feedback.record({
    type: 'daily_reflection',
    tags: ['Heavy workload', 'Exams', 'Chill day'],
    note: 'Long day but ended okay.',
    timestamp: '2026-05-04T18:30:00+05:30'
  });
  assert.strictEqual(reflection.ok, true);
  assert.ok(reflection.updated.learned.includes('Heavy workload +15'));
  const memoryAfterReflection = store.readAll();
  assert.ok(memoryAfterReflection.profile.stress_triggers.adjustment !== 0);
  assert.ok(memoryAfterReflection.routine.routines.time_based_stress_trends.length > 0);

  const rehydrated = routineMemory.hydrateContext({
    ...normalized,
    locationState: 'unknown',
    nextMeetingTitle: 'Design 1:1',
    dayOfWeek: 'friday',
    permissions: {
      notificationPolicyAccess: true,
      writeSettings: true
    }
  });

  decision = safety.evaluate(rehydrated, {
    type: 'APPLY_PACK',
    packId: 'office_focus_high_stress',
    confidence: 0.95,
    stress: { score: 80, level: 'critical' },
    actions: ['DND_ON'],
    suggestedActions: [],
    blockedByPermission: [],
    explanation: 'test'
  });

  assert.strictEqual(decision.type, 'OBSERVE');
  assert.strictEqual(decision.safety.status, 'blocked');

  const quietHoursDecision = safety.evaluate({ ...rehydrated, hourOfDay: 23, minuteOfHour: 15 }, {
    type: 'APPLY_PACK',
    packId: 'commute_alert',
    confidence: 0.91,
    stress: { score: 66, level: 'high' },
    actions: ['SEND_DEPARTURE_ALERT'],
    suggestedActions: [],
    blockedByPermission: [],
    explanation: 'test',
    commutePlan: {
      source: 'heuristic',
      etaMinutes: 30,
      bufferMinutes: 15,
      leaveInMinutes: 25,
      leaveByLocal: '11:55 PM',
      shouldAlert: true,
      reason: 'test'
    }
  });

  assert.strictEqual(quietHoursDecision.type, 'OBSERVE');
  assert.ok(quietHoursDecision.safety.blockers.includes('quiet hours suppress this automation'));

  const soul = fs.readFileSync(store.paths.soulPath, 'utf8');
  assert.ok(soul.includes('Avoid office_focus_high_stress on friday.'));
  console.log('CAPE agent module tests passed');
}

if (require.main === module) {
  runAgentModuleTests().catch(error => {
    console.error(error);
    process.exit(1);
  });
}

module.exports = {
  runAgentModuleTests
};
