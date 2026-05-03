const http = require('http');
const fs = require('fs');
const path = require('path');
const { buildDecision } = require('../../cape-core/src');
const { buildCommutePlan } = require('./commute-planner');
const { buildReasoningNote } = require('./ollama-reasoner');
const { createYamlMemoryStore } = require('./yaml-memory');
const { createRoutineMemoryAgent } = require('./routine-memory-agent');
const { createSafetyPermissionAgent } = require('./safety-permission-agent');
const { createFeedbackLearningAgent } = require('./feedback-learning-agent');

const DEFAULT_PORT = Number(process.env.CAPE_GATEWAY_PORT ?? 8787);
const DEFAULT_HOST = process.env.CAPE_GATEWAY_HOST ?? '127.0.0.1';
const MEMORY_DIR = process.env.OPENCLAW_CAPE_MEMORY_DIR ?? path.resolve(__dirname, '../../../openclaw/runtime');
const OPENCLAW_MEMORY_DIR = process.env.OPENCLAW_CAPE_PROFILE_DIR ?? path.resolve(__dirname, '../../../openclaw/memory');

function createServer() {
  const memoryStore = createYamlMemoryStore(OPENCLAW_MEMORY_DIR);
  const routineMemoryAgent = createRoutineMemoryAgent(memoryStore);
  const safetyAgent = createSafetyPermissionAgent();
  const feedbackLearningAgent = createFeedbackLearningAgent(memoryStore);

  return http.createServer(async (req, res) => {
    try {
      if (req.method === 'GET' && req.url === '/health') {
        return sendJson(res, 200, {
          ok: true,
          service: 'cape-gateway',
          version: '0.1.0'
        });
      }

      if (req.method === 'POST' && req.url === '/v1/context/decision') {
        const body = await readJson(req);
        const memoryHydratedContext = routineMemoryAgent.hydrateContext(body);
        const commutePlan = await buildCommutePlan(memoryHydratedContext);
        const contextWithPlan = { ...memoryHydratedContext, commutePlan };
        const previewDecision = buildDecision(contextWithPlan);
        const reasoningNote = await buildReasoningNote(contextWithPlan, previewDecision);
        const enrichedContext = { ...contextWithPlan, reasoningNote };
        const baseDecision = buildDecision(enrichedContext);
        const decision = safetyAgent.evaluate(enrichedContext, baseDecision);
        const agentTrace = buildAgentTrace(enrichedContext, decision);
        persistEvent({
          kind: 'context_decision',
          context: enrichedContext,
          decision,
          agentTrace
        });
        return sendJson(res, 200, {
          decision,
          agentTrace,
          openclaw: {
            workspaceHint: 'openclaw/runtime',
            recommendedModel: process.env.OLLAMA_MODEL ?? 'llama3.1:8b'
          }
        });
      }

      if (req.method === 'POST' && req.url === '/v1/feedback') {
        const body = await readJson(req);
        const learning = feedbackLearningAgent.record(body);
        persistEvent({
          kind: 'feedback',
          feedback: body,
          learning
        });
        return sendJson(res, 200, {
          ok: true,
          message: 'feedback_recorded',
          learning
        });
      }

      return sendJson(res, 404, { error: 'not_found' });
    } catch (error) {
      return sendJson(res, 400, {
        error: 'bad_request',
        message: error.message
      });
    }
  });
}

function buildAgentTrace(context, decision) {
  const trace = [
    {
      agent: 'context-intake',
      status: 'ok',
      output: `Normalized ${Object.keys(context).length} context fields`
    },
    {
      agent: 'stress-scoring',
      status: 'ok',
      output: `${decision.stress.score}/100 ${decision.stress.level}`
    },
    {
      agent: 'routine-memory',
      status: 'ok',
      output: `min confidence ${context.memory?.minimumConfidenceToApply ?? 'n/a'}`
    },
    {
      agent: 'decision-orchestrator',
      status: 'ok',
      output: `${decision.type} ${decision.packId}`
    },
    {
      agent: 'commute-agent',
      status: context.commutePlan ? 'ok' : 'skipped',
      output: context.commutePlan
        ? `${context.commutePlan.source}: leave by ${context.commutePlan.leaveByLocal}`
        : 'no upcoming meeting window'
    },
    {
      agent: 'ollama-reasoning',
      status: context.reasoningNote?.startsWith('Ollama reasoning unavailable') ? 'fallback' : 'ok',
      output: context.reasoningNote || 'no note'
    },
    {
      agent: 'safety-permission',
      status: decision.safety?.status ?? (decision.blockedByPermission.length > 0 ? 'blocked' : 'ok'),
      output: decision.safety?.blockers?.length
        ? decision.safety.blockers.join(', ')
        : decision.blockedByPermission.length > 0
          ? decision.blockedByPermission.join(', ')
          : 'required permissions available'
    },
    {
      agent: 'pack-execution',
      status: decision.type === 'SUGGEST_PACK' ? 'suggest' : (decision.actions.length > 0 ? 'ready' : 'observe'),
      output: decision.actions.join(', ') || 'no actions'
    }
  ];
  return trace;
}

function persistEvent(event) {
  fs.mkdirSync(MEMORY_DIR, { recursive: true });
  const enriched = {
    ...event,
    recordedAt: new Date().toISOString()
  };
  fs.appendFileSync(path.join(MEMORY_DIR, 'events.jsonl'), `${JSON.stringify(enriched)}\n`);
  fs.writeFileSync(path.join(MEMORY_DIR, 'latest-context.json'), JSON.stringify(enriched, null, 2));
  fs.writeFileSync(path.join(MEMORY_DIR, 'latest-summary.md'), renderSummary(enriched));
}

function renderSummary(event) {
  if (event.kind === 'feedback') {
    return [
      '# CAPE Latest Feedback',
      '',
      `Recorded: ${event.recordedAt}`,
      `Pack: ${event.feedback.packId ?? 'unknown'}`,
      `Signal: ${event.feedback.signal ?? 'unknown'}`,
      `Note: ${event.feedback.note ?? ''}`,
      `Learning: ${(event.learning?.updated?.learned ?? []).join(', ') || 'none'}`,
      ''
    ].join('\n');
  }

  const lines = [
    '# CAPE Latest Context Decision',
    '',
    `Recorded: ${event.recordedAt}`,
    `Decision: ${event.decision.type}`,
    `Pack: ${event.decision.packId}`,
    `Stress: ${event.decision.stress.score}/100 ${event.decision.stress.level}`,
    `Actions: ${event.decision.actions.join(', ') || 'none'}`,
    `Blocked: ${event.decision.blockedByPermission.join(', ') || 'none'}`,
    event.decision.commutePlan ? `Commute: leave by ${event.decision.commutePlan.leaveByLocal} (${event.decision.commutePlan.source})` : 'Commute: none',
    event.decision.reasoningNote ? `Reasoning: ${event.decision.reasoningNote}` : 'Reasoning: none',
    '',
    '## Agent Trace',
    ...event.agentTrace.map(item => `- ${item.agent}: ${item.status} - ${item.output}`),
    '',
    '## Explanation',
    event.decision.explanation,
    ''
  ];
  return lines.join('\n');
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', chunk => {
      raw += chunk;
      if (raw.length > 1_000_000) {
        reject(new Error('request_too_large'));
        req.destroy();
      }
    });
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        reject(new Error('invalid_json'));
      }
    });
    req.on('error', reject);
  });
}

function sendJson(res, status, body) {
  const payload = JSON.stringify(body, null, 2);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(payload)
  });
  res.end(payload);
}

if (require.main === module) {
  const server = createServer();
  server.listen(DEFAULT_PORT, DEFAULT_HOST, () => {
    console.log(`CAPE gateway listening on http://${DEFAULT_HOST}:${DEFAULT_PORT}`);
    console.log(`CAPE memory writing to ${MEMORY_DIR}`);
  });
}

module.exports = {
  createServer
};
