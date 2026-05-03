function createRoutineMemoryAgent(store) {
  function hydrateContext(rawContext) {
    const { profile, routine } = store.readAll();
    const minConfidence = Number(profile.preferences?.minimum_confidence_to_apply ?? 0.78);
    const automationStyle = profile.preferences?.automation_style ?? 'ask_when_uncertain';

    const packFeedback = routine.pack_feedback ?? {};
    const overrides = routine.overrides ?? {};

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
        blockedKeywords: Array.isArray(overrides.blocked_keywords) ? overrides.blocked_keywords : []
      }
    };
  }

  return {
    hydrateContext
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

module.exports = {
  createRoutineMemoryAgent
};
