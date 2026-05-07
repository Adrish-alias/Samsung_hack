const fs = require('fs');
const path = require('path');
const { buildReasoningNote } = require('./ollama-reasoner');
const { createContextIntakeAgent } = require('./context-intake-agent');
const { createStressScoringAgent } = require('./stress-scoring-agent');
const { createCommuteAgent } = require('./commute-agent');
const { createDecisionOrchestratorAgent } = require('./decision-orchestrator-agent');
const { createYamlMemoryStore } = require('./yaml-memory');
const { createRoutineMemoryAgent } = require('./routine-memory-agent');
const { createSafetyPermissionAgent } = require('./safety-permission-agent');
const { createFeedbackLearningAgent } = require('./feedback-learning-agent');
const { createOpenClawRuntimeClient } = require('./openclaw-runtime-client');

function createOpenClawOrchestrator(options = {}) {
  const runtimeDir = path.resolve(options.runtimeDir);
  const memoryDir = path.resolve(options.memoryDir);
  const reasoningNoteBuilder = options.reasoningNoteBuilder ?? buildReasoningNote;
  const memoryStore = createYamlMemoryStore(memoryDir);
  const contextIntakeAgent = createContextIntakeAgent({ now: options.now });
  const routineMemoryAgent = createRoutineMemoryAgent(memoryStore);
  const stressScoringAgent = createStressScoringAgent();
  const commuteAgent = createCommuteAgent(options.commuteAgentOptions);
  const decisionOrchestratorAgent = createDecisionOrchestratorAgent();
  const safetyAgent = createSafetyPermissionAgent();
  const feedbackLearningAgent = createFeedbackLearningAgent(memoryStore);
  const runtimeClient = options.openclawRuntimeClient ?? createOpenClawRuntimeClient(options.openclawRuntime);

  async function runContextDecision(rawContext) {
    if (runtimeClient.configured) {
      try {
        const remote = await runtimeClient.runContextDecision(rawContext);
        const event = normalizeRemoteContextEvent(remote, runtimeClient);
        persistSession(runtimeDir, event);
        return {
          decision: event.decision,
          agentTrace: event.agentTrace,
          openclaw: {
            ...event.openclaw,
            runtime: 'remote',
            baseUrl: runtimeClient.baseUrl,
            agentId: runtimeClient.agentId
          }
        };
      } catch (error) {
        if (runtimeClient.required) throw error;
        return runLocalContextDecision(rawContext, {
          fallbackReason: error.message,
          attemptedRemote: true
        });
      }
    }

    if (runtimeClient.required) {
      throw new Error('OPENCLAW_REQUIRED is true but OPENCLAW_BASE_URL/OPENCLAW_TOKEN are not configured');
    }

    return runLocalContextDecision(rawContext, {
      fallbackReason: 'openclaw_not_configured',
      attemptedRemote: false
    });
  }

  async function runLocalContextDecision(rawContext, fallback = {}) {
    const session = createSession('context_decision');
    const contextEvents = [];

    const normalizedContext = step(session, 'context-intake', () => {
      const normalized = contextIntakeAgent.normalize(rawContext);
      contextEvents.push({
        stage: 'normalized',
        context: publicContext(normalized)
      });
      return normalized;
    }, normalized => `Normalized ${normalized.rawSignalCount ?? Object.keys(normalized).length} raw signals into CAPE context`);

    const memoryHydratedContext = step(session, 'routine-memory', () => {
      const hydrated = routineMemoryAgent.hydrateContext(normalizedContext);
      contextEvents.push({
        stage: 'memory_hydrated',
        context: publicContext(hydrated)
      });
      return hydrated;
    }, hydrated => `min confidence ${hydrated.memory?.minimumConfidenceToApply ?? 'n/a'}`);

    const stress = step(session, 'stress-scoring', () => {
      return stressScoringAgent.score(memoryHydratedContext);
    }, result => result.summary);

    const commutePlan = await stepAsync(session, 'commute-agent', async () => {
      return commuteAgent.plan({ ...memoryHydratedContext, stress });
    }, plan => plan ? `${plan.source}: leave by ${plan.leaveByLocal}` : 'no upcoming meeting window');

    const previewDecision = step(session, 'decision-orchestrator', () => {
      return decisionOrchestratorAgent.decide({
        context: memoryHydratedContext,
        stress,
        commutePlan
      });
    }, decision => `${decision.type} ${decision.packId} (${decision.confidence.toFixed(2)})`);

    const reasoningNote = await stepAsync(session, 'ollama-reasoning', async () => {
      return reasoningNoteBuilder({ ...memoryHydratedContext, stress, commutePlan }, previewDecision);
    }, note => note || 'no note', note => note?.startsWith('Ollama reasoning unavailable') ? 'fallback' : 'ok');

    const baseDecision = {
      ...previewDecision,
      reasoningNote
    };

    const decision = step(session, 'safety-permission', () => {
      return safetyAgent.evaluate({ ...memoryHydratedContext, stress, commutePlan, reasoningNote }, baseDecision);
    }, safeDecision => {
      if (safeDecision.safety?.blockers?.length) return safeDecision.safety.blockers.join(', ');
      if (safeDecision.blockedByPermission.length > 0) return safeDecision.blockedByPermission.join(', ');
      return 'required permissions available';
    }, safeDecision => safeDecision.safety?.status ?? (safeDecision.blockedByPermission.length > 0 ? 'blocked' : 'ok'));

    step(session, 'pack-execution', () => decision, safeDecision => {
      return safeDecision.actions.join(', ') || 'no actions';
    }, safeDecision => safeDecision.type === 'SUGGEST_PACK' ? 'suggest' : (safeDecision.actions.length > 0 ? 'ready' : 'observe'));

    routineMemoryAgent.rememberObservation(memoryHydratedContext, stress, commutePlan, decision);

    const event = finishSession(session, {
      kind: 'context_decision',
      context: publicContext(memoryHydratedContext),
      contextEvents,
      decision,
      agentTrace: session.agentTrace
    });
    persistSession(runtimeDir, event);

    return {
      decision,
      agentTrace: session.agentTrace,
      openclaw: {
        ...openClawMetadata(session),
        runtime: 'local-fallback',
        fallbackReason: fallback.fallbackReason,
        attemptedRemote: fallback.attemptedRemote
      }
    };
  }

  async function recordFeedback(feedback) {
    if (runtimeClient.configured) {
      try {
        const remote = await runtimeClient.recordFeedback(feedback);
        const event = normalizeRemoteFeedbackEvent(remote, feedback, runtimeClient);
        persistSession(runtimeDir, event);
        return {
          learning: event.learning,
          openclaw: {
            ...event.openclaw,
            runtime: 'remote',
            baseUrl: runtimeClient.baseUrl,
            agentId: runtimeClient.agentId
          }
        };
      } catch (error) {
        if (runtimeClient.required) throw error;
        return recordLocalFeedback(feedback, {
          fallbackReason: error.message,
          attemptedRemote: true
        });
      }
    }

    if (runtimeClient.required) {
      throw new Error('OPENCLAW_REQUIRED is true but OPENCLAW_BASE_URL/OPENCLAW_TOKEN are not configured');
    }

    return recordLocalFeedback(feedback, {
      fallbackReason: 'openclaw_not_configured',
      attemptedRemote: false
    });
  }

  function recordLocalFeedback(feedback, fallback = {}) {
    const session = createSession('feedback');
    const learning = step(session, 'feedback-learning', () => {
      return feedbackLearningAgent.record(feedback);
    }, result => (result.updated?.learned ?? []).join(', ') || 'no new rules');

    appendFeedbackMemory(memoryDir, feedback, learning);
    const event = finishSession(session, {
      kind: 'feedback',
      feedback,
      learning
    });
    persistSession(runtimeDir, event);

    return {
      learning,
      openclaw: {
        ...openClawMetadata(session),
        runtime: 'local-fallback',
        fallbackReason: fallback.fallbackReason,
        attemptedRemote: fallback.attemptedRemote
      }
    };
  }

  return {
    runContextDecision,
    recordFeedback
  };
}

function createSession(kind) {
  const startedAt = new Date().toISOString();
  return {
    id: `cape-${kind}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
    kind,
    startedAt,
    agentTrace: []
  };
}

function step(session, agent, fn, outputBuilder, statusBuilder = () => 'ok') {
  try {
    const result = fn();
    session.agentTrace.push({
      agent,
      status: statusBuilder(result),
      output: outputBuilder(result)
    });
    return result;
  } catch (error) {
    session.agentTrace.push({
      agent,
      status: 'error',
      output: error.message
    });
    throw error;
  }
}

async function stepAsync(session, agent, fn, outputBuilder, statusBuilder = () => 'ok') {
  try {
    const result = await fn();
    session.agentTrace.push({
      agent,
      status: statusBuilder(result),
      output: outputBuilder(result)
    });
    return result;
  } catch (error) {
    session.agentTrace.push({
      agent,
      status: 'error',
      output: error.message
    });
    throw error;
  }
}

function finishSession(session, event) {
  const completedAt = new Date().toISOString();
  return {
    ...event,
    agentTrace: event.agentTrace ?? session.agentTrace,
    sessionId: session.id,
    openclawSession: {
      id: session.id,
      kind: session.kind,
      startedAt: session.startedAt,
      completedAt,
      agentCount: session.agentTrace.length,
      status: session.agentTrace.some(item => item.status === 'error') ? 'error' : 'ok'
    },
    recordedAt: completedAt
  };
}

function openClawMetadata(session) {
  return {
    orchestrator: 'openclaw',
    mode: 'gateway-runtime',
    sessionId: session.id,
    workspaceHint: 'openclaw/runtime',
    contextLog: 'openclaw/runtime/context-log.jsonl',
    sessionLog: 'openclaw/runtime/session-log.jsonl',
    recommendedModel: process.env.OLLAMA_MODEL ?? 'llama3.1:8b'
  };
}

function normalizeRemoteContextEvent(remote, runtimeClient) {
  const session = createSession('context_decision');
  const decision = remote.decision;
  if (!decision) throw new Error('openclaw_response_missing_decision');
  const agentTrace = Array.isArray(remote.agentTrace) ? remote.agentTrace : [];
  const event = finishSession({ ...session, agentTrace }, {
    kind: 'context_decision',
    context: remote.context ?? {},
    contextEvents: remote.contextEvents ?? [],
    decision,
    agentTrace,
    openclaw: {
      ...(remote.openclaw ?? {}),
      orchestrator: 'openclaw',
      mode: 'gateway-runtime',
      runtime: 'remote',
      baseUrl: runtimeClient.baseUrl,
      agentId: runtimeClient.agentId,
      sessionId: remote.openclaw?.sessionId
    }
  });
  event.openclaw = {
    ...openClawMetadata({ ...session, id: event.sessionId }),
    ...(event.openclaw ?? {}),
    sessionId: event.sessionId
  };
  return event;
}

function normalizeRemoteFeedbackEvent(remote, feedback, runtimeClient) {
  const session = createSession('feedback');
  const learning = remote.learning;
  if (!learning) throw new Error('openclaw_response_missing_learning');
  const agentTrace = Array.isArray(remote.agentTrace) ? remote.agentTrace : [];
  const event = finishSession({ ...session, agentTrace }, {
    kind: 'feedback',
    feedback,
    learning,
    agentTrace,
    openclaw: {
      ...(remote.openclaw ?? {}),
      orchestrator: 'openclaw',
      mode: 'gateway-runtime',
      runtime: 'remote',
      baseUrl: runtimeClient.baseUrl,
      agentId: runtimeClient.agentId,
      sessionId: remote.openclaw?.sessionId
    }
  });
  event.openclaw = {
    ...openClawMetadata({ ...session, id: event.sessionId }),
    ...(event.openclaw ?? {}),
    sessionId: event.sessionId
  };
  return event;
}

function persistSession(runtimeDir, event) {
  fs.mkdirSync(runtimeDir, { recursive: true });
  fs.mkdirSync(path.join(runtimeDir, 'sessions'), { recursive: true });

  fs.appendFileSync(path.join(runtimeDir, 'events.jsonl'), `${JSON.stringify(event)}\n`);
  fs.appendFileSync(path.join(runtimeDir, 'session-log.jsonl'), `${JSON.stringify(event.openclawSession)}\n`);
  if (event.kind === 'context_decision') {
    fs.appendFileSync(path.join(runtimeDir, 'context-log.jsonl'), `${JSON.stringify({
      sessionId: event.sessionId,
      recordedAt: event.recordedAt,
      context: event.context,
      contextEvents: event.contextEvents
    })}\n`);
  }

  fs.writeFileSync(path.join(runtimeDir, 'latest-context.json'), JSON.stringify(event, null, 2));
  fs.writeFileSync(path.join(runtimeDir, 'latest-summary.md'), renderSummary(event));
  fs.writeFileSync(path.join(runtimeDir, 'latest-session.md'), renderSession(event));
  fs.writeFileSync(path.join(runtimeDir, 'sessions', `${event.sessionId}.json`), JSON.stringify(event, null, 2));
}

function renderSummary(event) {
  if (event.kind === 'feedback') {
    return [
      '# CAPE Latest Feedback',
      '',
      `Session: ${event.sessionId}`,
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
    `Session: ${event.sessionId}`,
    `Recorded: ${event.recordedAt}`,
    `Decision: ${event.decision.type}`,
    `Pack: ${event.decision.packId}`,
    `Confidence: ${event.decision.confidence?.toFixed?.(2) ?? 'n/a'}`,
    `Stress: ${event.decision.stress.score}/100 ${event.decision.stress.level}`,
    `Actions: ${event.decision.actions.join(', ') || 'none'}`,
    `Blocked: ${event.decision.blockedByPermission.join(', ') || 'none'}`,
    event.decision.commutePlan ? `Commute: leave by ${event.decision.commutePlan.leaveByLocal} (${event.decision.commutePlan.source})` : 'Commute: none',
    event.decision.reasoningNote ? `Reasoning: ${event.decision.reasoningNote}` : 'Reasoning: none',
    '',
    '## OpenClaw Agent Trace',
    ...event.agentTrace.map(item => `- ${item.agent}: ${item.status} - ${item.output}`),
    '',
    '## Explanation',
    event.decision.explanation,
    ''
  ];
  return lines.join('\n');
}

function renderSession(event) {
  return [
    '# CAPE OpenClaw Session',
    '',
    `Session: ${event.sessionId}`,
    `Kind: ${event.kind}`,
    `Status: ${event.openclawSession.status}`,
    `Started: ${event.openclawSession.startedAt}`,
    `Completed: ${event.openclawSession.completedAt}`,
    '',
    '## Agents',
    ...event.agentTrace.map((item, index) => `${index + 1}. ${item.agent} [${item.status}] - ${item.output}`),
    ''
  ].join('\n');
}

function publicContext(context) {
  const { memory, ...publicFields } = context;
  return publicFields;
}

function appendFeedbackMemory(memoryDir, feedback, learning) {
  const memoryFile = path.join(memoryDir, 'MEMORY.md');
  const learned = learning?.updated?.learned ?? [];
  const lines = [
    `## ${new Date().toISOString()} feedback`,
    `- pack: ${feedback.packId ?? 'unknown'}`,
    `- signal: ${feedback.signal ?? 'unknown'}`,
    `- note: ${String(feedback.note ?? '').trim() || 'none'}`,
    `- learned: ${learned.join(', ') || 'none'}`,
    ''
  ].join('\n');
  fs.mkdirSync(memoryDir, { recursive: true });
  if (!fs.existsSync(memoryFile)) {
    fs.writeFileSync(memoryFile, '# CAPE Adaptive Memory\n\n');
  }
  fs.appendFileSync(memoryFile, lines);
}

module.exports = {
  createOpenClawOrchestrator,
  publicContext,
  renderSummary,
  renderSession
};
