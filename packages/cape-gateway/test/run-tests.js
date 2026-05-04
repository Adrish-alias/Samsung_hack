const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { createServer } = require('../src/server');
const { runAgentModuleTests } = require('./agents.test');

async function main() {
  await runAgentModuleTests();
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
        calendar: true,
        location: true
      }
    })
  });

  assert.ok(['APPLY_PACK', 'SUGGEST_PACK'].includes(decision.decision.type));
  assert.ok(['office_focus_high_stress', 'commute_alert'].includes(decision.decision.packId));
  assert.ok(Array.isArray(decision.agentTrace));
  assert.strictEqual(decision.agentTrace.length, 8);
  assert.strictEqual(decision.openclaw.recommendedModel, 'llama3.1:8b');
  assert.strictEqual(decision.decision.reasoningNote, 'Test reasoning note');

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
