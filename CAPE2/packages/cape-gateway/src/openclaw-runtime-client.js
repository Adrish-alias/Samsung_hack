function createOpenClawRuntimeClient(options = {}) {
  const baseUrl = normalizeBaseUrl(options.baseUrl ?? process.env.OPENCLAW_BASE_URL);
  const token = options.token ?? process.env.OPENCLAW_TOKEN;
  const agentId = options.agentId ?? process.env.OPENCLAW_AGENT_ID ?? 'cape';
  const timeoutMs = Number(options.timeoutMs ?? process.env.OPENCLAW_TIMEOUT_MS ?? 45000);
  const method = options.method ?? process.env.OPENCLAW_AGENT_METHOD ?? 'agent';
  const required = asBoolean(options.required ?? process.env.OPENCLAW_REQUIRED);
  const configured = Boolean(
    baseUrl &&
    token &&
    !String(token).startsWith('replace_with') &&
    !String(baseUrl).includes('replace_with')
  );

  async function runContextDecision(rawContext) {
    const response = await requestAgent({
      kind: 'context_decision',
      commandBody: 'Run CAPE context decision orchestration.',
      body: buildContextPrompt(rawContext)
    });
    return parseOpenClawJson(response);
  }

  async function recordFeedback(feedback) {
    const response = await requestAgent({
      kind: 'feedback',
      commandBody: 'Run CAPE feedback learning orchestration.',
      body: buildFeedbackPrompt(feedback)
    });
    return parseOpenClawJson(response);
  }

  function requestAgent({ kind, body, commandBody }) {
    if (!configured) throw new Error('openclaw_not_configured');
    const wsUrl = toWebSocketUrl(baseUrl);
    const sessionKey = `cape:${kind}`;
    const requestId = `cape-${kind}-${Date.now()}`;

    return withTimeout(new Promise((resolve, reject) => {
      const socket = new WebSocket(wsUrl);
      let connected = false;
      let settled = false;

      function finish(error, value) {
        if (settled) return;
        settled = true;
        try {
          socket.close();
        } catch {
          // ignore close errors
        }
        if (error) reject(error);
        else resolve(value);
      }

      socket.addEventListener('open', () => {
        socket.send(JSON.stringify({
          type: 'connect',
          params: {
            role: 'client',
            client: {
              name: 'cape-gateway',
              version: '0.1.0'
            },
            auth: {
              token
            }
          }
        }));
      });

      socket.addEventListener('message', event => {
        const message = parseFrame(event.data);
        if (!message) return;

        if (!connected && (message.type === 'hello-ok' || message.type === 'connected' || message.ok === true)) {
          connected = true;
          socket.send(JSON.stringify({
            type: 'req',
            id: requestId,
            method,
            params: {
              agentId,
              sessionKey,
              body,
              commandBody,
              idempotencyKey: requestId,
              metadata: {
                source: 'cape-gateway',
                kind,
                responseFormat: 'json'
              }
            }
          }));
          return;
        }

        if (message.type === 'res' && message.id === requestId) {
          if (message.ok === false) {
            finish(new Error(message.error?.message || message.error || 'openclaw_agent_request_failed'));
            return;
          }
          finish(null, message.payload ?? message.result ?? message);
        }
      });

      socket.addEventListener('error', () => {
        finish(new Error(`openclaw_ws_error:${wsUrl}`));
      });

      socket.addEventListener('close', () => {
        if (!settled) finish(new Error('openclaw_ws_closed_before_response'));
      });
    }), timeoutMs, 'openclaw_timeout');
  }

  return {
    configured,
    required,
    baseUrl,
    agentId,
    runContextDecision,
    recordFeedback
  };
}

function buildContextPrompt(rawContext) {
  return [
    'You are the OpenClaw-owned CAPE multi-agent orchestrator.',
    'Run the CAPE agents in order: Context Intake, Routine Memory, Stress Scoring, Commute, Decision Orchestrator, Safety Permission, Pack Execution readiness.',
    'You are running inside the user\'s OpenClaw workspace.',
    'Use the workspace files: AGENTS.md, SOUL.md, TOOLS.md.',
    'Use memory YAML: memory/*.yaml (e.g. memory/user_profile.yaml, memory/routine_patterns.yaml).',
    'Use behavior packs: skills/*/SKILL.md.',
    'Return ONLY valid JSON with keys: decision, agentTrace, openclaw.',
    'The decision must match the Android CAPE schema: type, packId, stress, actions, suggestedActions, blockedByPermission, explanation, confidence, commutePlan, reasoningNote, safety.',
    'Raw Android context:',
    JSON.stringify(rawContext, null, 2)
  ].join('\n\n');
}

function buildFeedbackPrompt(feedback) {
  return [
    'You are the OpenClaw-owned CAPE Feedback Learning Agent.',
    'Update memory from this feedback using SOUL.md and memory/*.yaml conventions.',
    'If a new user-respect rule is learned, append it to SOUL.md in plain English.',
    'Return ONLY valid JSON with keys: learning, openclaw.',
    'Feedback:',
    JSON.stringify(feedback, null, 2)
  ].join('\n\n');
}

function parseOpenClawJson(response) {
  if (response && typeof response === 'object') {
    if (response.decision || response.learning) return response;
    const text = response.text ?? response.body ?? response.content ?? response.message;
    if (text) return parseOpenClawJson(text);
  }
  const text = String(response ?? '').trim();
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i);
  const jsonText = fenced ? fenced[1].trim() : text.slice(text.indexOf('{'), text.lastIndexOf('}') + 1);
  if (!jsonText || jsonText === text && !text.startsWith('{')) {
    throw new Error('openclaw_response_missing_json');
  }
  return JSON.parse(jsonText);
}

function parseFrame(data) {
  try {
    return JSON.parse(String(data));
  } catch {
    return null;
  }
}

function normalizeBaseUrl(value) {
  const text = String(value ?? '').trim();
  return text ? text.replace(/\/$/, '') : null;
}

function toWebSocketUrl(value) {
  return value.replace(/^http:/, 'ws:').replace(/^https:/, 'wss:');
}

function withTimeout(promise, timeoutMs, label) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(label)), timeoutMs);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

function asBoolean(value) {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'string') return value.toLowerCase() === 'true';
  return Boolean(value);
}

module.exports = {
  createOpenClawRuntimeClient,
  parseOpenClawJson
};
