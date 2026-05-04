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
    permissions: {
      notificationPolicyAccess: true,
      writeSettings: true,
      calendar: true,
      location: true
    }
  });

  assert.strictEqual(normalized.dayOfWeek, 'monday');
  const hydrated = routineMemory.hydrateContext(normalized);
  assert.strictEqual(hydrated.memory.minimumConfidenceToApply, 0.78);

  const stress = stressAgent.score(hydrated);
  assert.strictEqual(stress.level, 'high');

  const commutePlan = await commuteAgent.plan({ ...hydrated, stress });
  const preview = orchestrator.decide({ context: hydrated, stress, commutePlan });
  assert.strictEqual(preview.packId, 'commute_alert');

  routineMemory.rememberObservation(hydrated, stress, commutePlan, preview);

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

  const rehydrated = routineMemory.hydrateContext({
    ...normalized,
    locationState: 'office',
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
