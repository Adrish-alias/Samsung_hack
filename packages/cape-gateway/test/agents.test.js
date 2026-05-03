const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { createYamlMemoryStore } = require('../src/yaml-memory');
const { createRoutineMemoryAgent } = require('../src/routine-memory-agent');
const { createSafetyPermissionAgent } = require('../src/safety-permission-agent');
const { createFeedbackLearningAgent } = require('../src/feedback-learning-agent');

function main() {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'cape-memory-'));
  const store = createYamlMemoryStore(path.join(tempDir, 'memory'));
  store.ensure();

  const routineMemory = createRoutineMemoryAgent(store);
  const safety = createSafetyPermissionAgent();
  const feedback = createFeedbackLearningAgent(store);

  const hydrated = routineMemory.hydrateContext({
    locationState: 'office',
    nextMeetingTitle: 'Weekly 1:1',
    permissions: {
      notificationPolicyAccess: true,
      writeSettings: true
    }
  });

  assert.strictEqual(hydrated.memory.minimumConfidenceToApply, 0.78);

  let decision = safety.evaluate(hydrated, {
    type: 'APPLY_PACK',
    packId: 'office_focus_high_stress',
    confidence: 0.7,
    stress: { score: 70, level: 'high' },
    actions: ['DND_ON'],
    blockedByPermission: [],
    explanation: 'test'
  });

  assert.strictEqual(decision.type, 'SUGGEST_PACK');

  const learned = feedback.record({
    packId: 'office_focus_high_stress',
    signal: 'rejected',
    note: "don't do this during 1:1s"
  });

  assert.strictEqual(learned.ok, true);
  assert.ok(learned.updated.learned.includes('keyword override 1:1s'));

  const rehydrated = routineMemory.hydrateContext({
    locationState: 'office',
    nextMeetingTitle: 'Design 1:1',
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
    blockedByPermission: [],
    explanation: 'test'
  });

  assert.strictEqual(decision.type, 'OBSERVE');
  assert.strictEqual(decision.safety.status, 'blocked');

  const soul = fs.readFileSync(store.paths.soulPath, 'utf8');
  assert.ok(soul.includes('Avoid automation when context suggests 1:1s.'));

  console.log('CAPE agent module tests passed');
}

main();
