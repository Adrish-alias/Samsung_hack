function createSafetyPermissionAgent() {
  function evaluate(context, decision) {
    const minimumConfidence = Number(context.memory?.minimumConfidenceToApply ?? 0.78);
    const automationStyle = context.memory?.automationStyle ?? 'ask_when_uncertain';
    const blockedPacks = new Set(context.memory?.blockedPacks ?? []);
    const blockedKeywords = (context.memory?.blockedKeywords ?? []).map(String);
    const packId = decision.packId;
    const note = String(context.nextMeetingTitle ?? context.feedbackNote ?? context.overrideText ?? '').toLowerCase();
    const rejectionBias = Number(context.memory?.rejectionBias?.[packId] ?? 0);
    const packBlockedByKeyword = blockedKeywords.find(keyword => keyword && note.includes(keyword.toLowerCase()));
    const packExplicitlyBlocked = blockedPacks.has(packId);
    const shouldSuggest = automationStyle === 'ask_when_uncertain' && decision.confidence < minimumConfidence;
    const learnedRejection = rejectionBias >= 0.6;

    const blockers = [];
    if (decision.blockedByPermission.length > 0) {
      blockers.push(`missing permissions: ${decision.blockedByPermission.join(', ')}`);
    }
    if (packExplicitlyBlocked) {
      blockers.push(`user blocked pack ${packId}`);
    }
    if (packBlockedByKeyword) {
      blockers.push(`user override for keyword "${packBlockedByKeyword}"`);
    }
    if (learnedRejection) {
      blockers.push(`historically rejected ${packId}`);
    }

    if (blockers.length > 0) {
      return {
        ...decision,
        type: 'OBSERVE',
        actions: [],
        safety: {
          status: 'blocked',
          blockers,
          suggested: false
        },
        explanation: `Safety agent blocked ${packId}: ${blockers.join('; ')}.`
      };
    }

    if (shouldSuggest && decision.actions.length > 0) {
      return {
        ...decision,
        type: 'SUGGEST_PACK',
        actions: [],
        safety: {
          status: 'suggest',
          blockers: [],
          suggested: true
        },
        explanation: `Confidence ${decision.confidence.toFixed(2)} is below the apply threshold ${minimumConfidence.toFixed(2)}; CAPE should suggest ${packId} instead of applying it.`
      };
    }

    return {
      ...decision,
      safety: {
        status: 'ok',
        blockers: [],
        suggested: false
      }
    };
  }

  return {
    evaluate
  };
}

module.exports = {
  createSafetyPermissionAgent
};
