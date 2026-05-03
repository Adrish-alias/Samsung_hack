const assert = require('assert');
const { createServer } = require('../src/server');
require('./agents.test');

async function main() {
  const server = createServer();
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
      permissions: {
        notificationPolicyAccess: true,
        writeSettings: true
      }
    })
  });

  assert.strictEqual(decision.decision.type, 'APPLY_PACK');
  assert.strictEqual(decision.decision.packId, 'office_focus_high_stress');
  assert.ok(Array.isArray(decision.agentTrace));
  assert.ok(decision.agentTrace.length >= 4);
  assert.strictEqual(decision.openclaw.recommendedModel, 'llama3.1:8b');

  const feedback = await fetchJson(`${baseUrl}/v1/feedback`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      packId: decision.decision.packId,
      signal: 'accepted',
      note: 'test feedback'
    })
  });

  assert.strictEqual(feedback.ok, true);

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
