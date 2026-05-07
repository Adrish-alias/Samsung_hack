const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { createServer } = require('../src/server');
const { createOpenClawOrchestrator } = require('../src/openclaw-orchestrator');
const { runAgentModuleTests } = require('./agents.test');
const { parseDurationSeconds, roundToNearestFiveMinutes } = require('../src/commute-planner');

async function main() {
  await runAgentModuleTests();
  assert.strictEqual(parseDurationSeconds('3600s'), 3600);
  assert.strictEqual(roundToNearestFiveMinutes(301), 300);
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'cape-gateway-'));
  const server = createServer({
    runtimeDir: path.join(tempDir, 'runtime'),
    memoryDir: path.join(tempDir, 'memory'),
    reasoningNoteBuilder: async () => 'Test reasoning note'
  });
  await listen(server);
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;

  const health = await fetchJson(`${baseUrl}/health`);
  assert.strictEqual(health.ok, true);
  assert.strictEqual(health.service, 'cape-gateway');

  const decision = await fetchJson(`${baseUrl}/v1/context/decision`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      locationState: 'office',
      sleepDebtMinutes: 95,
      meetingLoadToday: 7,
      commuteDelayMinutes: 22,
      screenTimeLast2hMinutes: 78,
      nextMeetingMinutes: 25,
      nextMeetingTitle: 'Weekly 1:1',
      permissions: {
        notificationPolicyAccess: true,
        writeSettings: true,
        notifications: true,
        calendar: true,
        location: true
      }
    })
  });

  assert.ok(['APPLY_PACK', 'SUGGEST_PACK'].includes(decision.decision.type));
  assert.ok(['office_focus_high_stress', 'commute_alert'].includes(decision.decision.packId));
  assert.ok(Array.isArray(decision.agentTrace));
  assert.strictEqual(decision.agentTrace.length, 8);
  assert.strictEqual(decision.openclaw.orchestrator, 'openclaw');
  assert.strictEqual(decision.openclaw.runtime, 'local-fallback');
  assert.strictEqual(decision.openclaw.fallbackReason, 'openclaw_not_configured');
  assert.ok(decision.openclaw.sessionId.startsWith('cape-context_decision-'));
  assert.strictEqual(decision.openclaw.recommendedModel, 'llama3.1:8b');
  assert.strictEqual(decision.decision.reasoningNote, 'Test reasoning note');
  assert.ok(decision.decision.commutePlan);
  assert.ok(decision.decision.commutePlan.etaMinutes > 0);
  assert.ok(decision.decision.commutePlan.modes.length > 0);
  assert.ok(fs.existsSync(path.join(tempDir, 'runtime', 'context-log.jsonl')));
  assert.ok(fs.existsSync(path.join(tempDir, 'runtime', 'session-log.jsonl')));
  assert.ok(fs.existsSync(path.join(tempDir, 'runtime', 'latest-session.md')));
  assert.ok(fs.existsSync(path.join(tempDir, 'runtime', 'sessions', `${decision.openclaw.sessionId}.json`)));

  const feedback = await fetchJson(`${baseUrl}/v1/feedback`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      packId: decision.decision.packId,
      signal: 'rejected',
      note: "don't do this during 1:1s on fridays"
    })
  });

  assert.strictEqual(feedback.ok, true);
  assert.ok(Array.isArray(feedback.learning.updated.learned));
  assert.strictEqual(feedback.openclaw.orchestrator, 'openclaw');
  assert.strictEqual(feedback.openclaw.runtime, 'local-fallback');
  assert.ok(feedback.openclaw.sessionId.startsWith('cape-feedback-'));

  const blockedDecision = await fetchJson(`${baseUrl}/v1/context/decision`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      locationState: 'commuting',
      sleepDebtMinutes: 30,
      meetingLoadToday: 2,
      commuteDelayMinutes: 30,
      screenTimeLast2hMinutes: 20,
      nextMeetingMinutes: 25,
      nextMeetingLocation: '12.9716,77.5946',
      permissions: {
        notificationPolicyAccess: true,
        writeSettings: true,
        notifications: false,
        calendar: true,
        location: true
      }
    })
  });
  assert.strictEqual(blockedDecision.decision.type, 'OBSERVE');
  assert.ok(blockedDecision.decision.safety.blockers.some(item => item.includes('notifications')));

  const remoteDir = fs.mkdtempSync(path.join(os.tmpdir(), 'cape-openclaw-remote-'));
  const remoteOpenClaw = createOpenClawOrchestrator({
    runtimeDir: path.join(remoteDir, 'runtime'),
    memoryDir: path.join(remoteDir, 'memory'),
    openclawRuntimeClient: {
      configured: true,
      required: true,
      baseUrl: 'ws://openclaw.test',
      agentId: 'cape',
      runContextDecision: async () => ({
        decision: {
          type: 'APPLY_PACK',
          packId: 'office_focus_high_stress',
          stress: { score: 80, level: 'high', reasons: ['remote_openclaw'] },
          actions: ['DND_ON'],
          suggestedActions: [],
          blockedByPermission: [],
          explanation: 'Remote OpenClaw selected the pack.',
          confidence: 0.91
        },
        agentTrace: [{ agent: 'openclaw-runtime', status: 'ok', output: 'remote orchestration complete' }],
        openclaw: { sessionId: 'remote-session-1' }
      }),
      recordFeedback: async () => ({
        learning: { ok: true, updated: { learned: ['remote feedback learned'] } },
        agentTrace: [{ agent: 'openclaw-runtime', status: 'ok', output: 'remote feedback complete' }],
        openclaw: { sessionId: 'remote-feedback-1' }
      })
    }
  });
  const remoteDecision = await remoteOpenClaw.runContextDecision({ locationState: 'office' });
  assert.strictEqual(remoteDecision.openclaw.runtime, 'remote');
  assert.strictEqual(remoteDecision.openclaw.baseUrl, 'ws://openclaw.test');
  assert.strictEqual(remoteDecision.decision.explanation, 'Remote OpenClaw selected the pack.');
  assert.ok(fs.existsSync(path.join(remoteDir, 'runtime', 'session-log.jsonl')));

  await close(server);
  console.log('All CAPE gateway tests passed');
}

function listen(server) {
  return new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
}

function close(server) {
  return new Promise(resolve => server.close(resolve));
}

async function fetchJson(url, options) {
  const response = await fetch(url, options);
  assert.ok(response.ok, `Expected ${url} to succeed`);
  return response.json();
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
