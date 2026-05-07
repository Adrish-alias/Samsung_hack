function createRoutineMemoryAgent(store) {
  function hydrateContext(rawContext) {
    const { profile, routine } = store.readAll();
    const minConfidence = Number(profile.preferences?.minimum_confidence_to_apply ?? 0.78);
    const automationStyle = profile.preferences?.automation_style ?? 'ask_when_uncertain';

    const packFeedback = routine.pack_feedback ?? {};
    const overrides = ensureOverrides(routine.overrides);

    return {
      ...rawContext,
      memory: {
        profile,
        routine,
        packFeedback,
        overrides,
        automationStyle,
        minimumConfidenceToApply: minConfidence,
        rejectionBias: buildRejectionBias(packFeedback),
        blockedPacks: Array.isArray(overrides.blocked_packs) ? overrides.blocked_packs : [],
        blockedKeywords: Array.isArray(overrides.blocked_keywords) ? overrides.blocked_keywords : [],
        conditionalBlocks: Array.isArray(overrides.conditional_blocks) ? overrides.conditional_blocks : []
      }
    };
  }

  function rememberObservation(context, stress, commutePlan, decision = null) {
    const snapshot = store.readAll();
    const profile = snapshot.profile;
    const routine = snapshot.routine;

    routine.locations ??= {};
    routine.locations.home ??= { confidence: 0.0, labels: [] };
    routine.locations.office ??= { confidence: 0.0, labels: [] };
    routine.routines ??= {};
    routine.routines.fatigue_windows ??= [];
    routine.routines.commute_patterns ??= [];
    routine.overrides = ensureOverrides(routine.overrides);
    routine.runtime_state ??= {};

    profile.user ??= {};
    profile.user.timezone = context.timezone ?? profile.user.timezone;
    profile.user.consent ??= {};
    profile.user.consent.notification_policy_access = Boolean(context.permissions?.notificationPolicyAccess);
    profile.user.consent.write_settings = Boolean(context.permissions?.writeSettings);

    if (context.locationState === 'home' || context.locationState === 'office') {
      const bucket = routine.locations[context.locationState];
      bucket.confidence = bumpConfidence(bucket.confidence);
      if (context.nextMeetingLocation) pushUnique(bucket.labels, context.nextMeetingLocation);
    }

    if (context.locationState === 'office' && context.hourOfDay != null) {
      const start = String(context.hourOfDay).padStart(2, '0');
      const end = String((context.hourOfDay + 1) % 24).padStart(2, '0');
      routine.routines.office_arrival_window = `${start}:00-${end}:00`;
    }

    if (stress.score >= 60 && context.dayOfWeek && context.hourOfDay != null) {
      pushUnique(routine.routines.fatigue_windows, `${context.dayOfWeek}@${context.hourOfDay}`);
    }

    if (commutePlan && context.nextMeetingLocation) {
      updateCommutePattern(routine.routines.commute_patterns, context.nextMeetingLocation, commutePlan, context.currentTimeIso);
    }

    if (decision && typeof decision === 'object') {
      if (decision.type === 'APPLY_PACK' && Array.isArray(decision.actions) && decision.actions.length > 0) {
        routine.runtime_state.last_applied_pack_id = decision.packId ?? null;
        routine.runtime_state.last_applied_at = context.currentTimeIso ?? new Date().toISOString();
        routine.runtime_state.last_applied_actions = decision.actions.slice(0, 24);
      }
      if (Array.isArray(decision.actions)) {
        if (decision.actions.includes('DND_ON')) {
          routine.runtime_state.dnd_on_since = routine.runtime_state.dnd_on_since ?? (context.currentTimeIso ?? new Date().toISOString());
        }
        if (decision.actions.includes('DND_OFF')) {
          routine.runtime_state.dnd_on_since = null;
        }
      }
    }

    store.writeProfile(profile);
    store.writeRoutine(routine);
  }

  return {
    hydrateContext,
    rememberObservation
  };
}

function buildRejectionBias(packFeedback) {
  const result = {};
  for (const [packId, counts] of Object.entries(packFeedback)) {
    const accepted = Number(counts.accepted ?? 0);
    const rejected = Number(counts.rejected ?? 0);
    const total = accepted + rejected;
    result[packId] = total === 0 ? 0 : rejected / total;
  }
  return result;
}

function ensureOverrides(overrides = {}) {
  return {
    blocked_packs: Array.isArray(overrides.blocked_packs) ? overrides.blocked_packs : [],
    blocked_keywords: Array.isArray(overrides.blocked_keywords) ? overrides.blocked_keywords : [],
    conditional_blocks: Array.isArray(overrides.conditional_blocks) ? overrides.conditional_blocks : [],
    notes: Array.isArray(overrides.notes) ? overrides.notes : []
  };
}

function bumpConfidence(value) {
  const numeric = Number(value ?? 0);
  return Math.min(1.0, Math.round(((numeric * 0.8) + 0.2) * 100) / 100);
}

function updateCommutePattern(patterns, destination, commutePlan, recordedAt) {
  const normalized = String(destination).trim().toLowerCase();
  let pattern = patterns.find(item => String(item.destination ?? '').trim().toLowerCase() === normalized);
  if (!pattern) {
    pattern = {
      destination,
      avg_eta_minutes: commutePlan.etaMinutes,
      observations: 1,
      last_recorded_at: recordedAt
    };
    patterns.push(pattern);
    return;
  }

  const observations = Number(pattern.observations ?? 0);
  const currentAverage = Number(pattern.avg_eta_minutes ?? commutePlan.etaMinutes);
  pattern.avg_eta_minutes = Math.round(((currentAverage * observations) + commutePlan.etaMinutes) / (observations + 1));
  pattern.observations = observations + 1;
  pattern.last_recorded_at = recordedAt;
}

function pushUnique(list, value) {
  if (!Array.isArray(list) || !value) return;
  if (!list.includes(value)) list.push(value);
}

module.exports = {
  createRoutineMemoryAgent
};
