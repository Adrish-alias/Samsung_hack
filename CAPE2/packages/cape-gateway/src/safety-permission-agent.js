function createSafetyPermissionAgent() {
  function evaluate(context, decision) {
    const minimumConfidence = Number(context.memory?.minimumConfidenceToApply ?? 0.78);
    const automationStyle = context.memory?.automationStyle ?? 'ask_when_uncertain';
    const blockedPacks = new Set(context.memory?.blockedPacks ?? []);
    const blockedKeywords = (context.memory?.blockedKeywords ?? []).map(String);
    const conditionalBlocks = Array.isArray(context.memory?.conditionalBlocks) ? context.memory.conditionalBlocks : [];
    const packId = decision.packId;
    const requiredLocationPack = isRequiredLocationPack(context, packId);
    const note = String(context.nextMeetingTitle ?? context.feedbackNote ?? context.overrideText ?? '').toLowerCase();
    const rejectionBias = Number(context.memory?.rejectionBias?.[packId] ?? 0);
    const packBlockedByKeyword = blockedKeywords.find(keyword => keyword && note.includes(keyword.toLowerCase()));
    const packExplicitlyBlocked = blockedPacks.has(packId);
    const conditionalBlock = matchingConditionalBlock(conditionalBlocks, packId, context, note);
    const shouldSuggest = automationStyle === 'ask_when_uncertain' && decision.confidence < minimumConfidence;
    const learnedRejection = rejectionBias >= 0.6;
    const quietHoursBlocked = blocksQuietHours(context, decision);
    const governor = evaluateGovernor(context, decision);

    const blockers = [];
    if (decision.blockedByPermission.length > 0) {
      blockers.push(`missing permissions: ${decision.blockedByPermission.join(', ')}`);
    }
    if (packExplicitlyBlocked && !requiredLocationPack) {
      blockers.push(`user blocked pack ${packId}`);
    }
    if (packBlockedByKeyword && !requiredLocationPack) {
      blockers.push(`user override for keyword "${packBlockedByKeyword}"`);
    }
    if (conditionalBlock && !requiredLocationPack) {
      blockers.push(conditionalBlock);
    }
    if (learnedRejection && !requiredLocationPack) {
      blockers.push(`historically rejected ${packId}`);
    }
    if (quietHoursBlocked && !(requiredLocationPack && packId === 'commute_alert')) {
      blockers.push('quiet hours suppress this automation');
    }
    if (governor.blockers.length > 0 && !requiredLocationPack) {
      blockers.push(...governor.blockers);
    }

    if (blockers.length > 0) {
      return {
        ...decision,
        type: 'OBSERVE',
        actions: [],
        suggestedActions: [],
        safety: {
          status: 'blocked',
          blockers,
          suggested: false
        },
        explanation: `Safety agent blocked ${packId}: ${blockers.join('; ')}.`
      };
    }

    if (governor.overrideDecision) {
      return {
        ...governor.overrideDecision,
        safety: {
          status: 'ok',
          blockers: [],
          suggested: false
        }
      };
    }

    if (shouldSuggest && decision.actions.length > 0) {
      return {
        ...decision,
        type: 'SUGGEST_PACK',
        suggestedActions: [...decision.actions],
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
      suggestedActions: decision.suggestedActions ?? [],
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

function evaluateGovernor(context, decision) {
  const runtime = context.memory?.routine?.runtime_state ?? {};
  const nowIso = context.currentTimeIso ?? new Date().toISOString();
  const nowMs = parseIsoMs(nowIso);

  const blockers = [];
  if (decision.type === 'APPLY_PACK' && Array.isArray(decision.actions) && decision.actions.length > 0) {
    const lastAppliedAtMs = parseIsoMs(runtime.last_applied_at);
    const lastPackId = String(runtime.last_applied_pack_id ?? '');
    const currentPack = String(decision.packId ?? '');
    const globalCooldownMin = Number(context.memory?.profile?.preferences?.cooldown_minutes ?? 20);
    const packCooldownMin = cooldownForPack(currentPack);

    if (lastAppliedAtMs && nowMs && currentPack) {
      const minutesSince = (nowMs - lastAppliedAtMs) / 60000;
      if (minutesSince >= 0 && minutesSince < globalCooldownMin) {
        blockers.push(`cooldown active (${Math.floor(minutesSince)}m < ${globalCooldownMin}m)`);
      }
      if (lastPackId === currentPack && minutesSince >= 0 && minutesSince < packCooldownMin) {
        blockers.push(`pack cooldown active for ${currentPack} (${Math.floor(minutesSince)}m < ${packCooldownMin}m)`);
      }
    }
  }

  const dndSinceMs = parseIsoMs(runtime.dnd_on_since);
  const maxDndMinutes = Number(context.memory?.profile?.preferences?.max_dnd_minutes ?? 90);
  if (dndSinceMs && nowMs) {
    const minutes = (nowMs - dndSinceMs) / 60000;
    if (minutes >= maxDndMinutes) {
      const hasDndPermission = Boolean(context.permissions?.notificationPolicyAccess);
      if (hasDndPermission) {
        return {
          blockers: [],
          overrideDecision: {
            ...decision,
            type: 'APPLY_PACK',
            packId: 'failsafe_release_dnd',
            confidence: 0.99,
            actions: ['DND_OFF', 'RINGER_NORMAL'],
            suggestedActions: [],
            blockedByPermission: [],
            explanation: `Failsafe: releasing DND after ${Math.floor(minutes)} minutes to prevent all-day silence.`,
            reasoningNote: 'Safety governor forced DND release.',
            safety: { status: 'ok', blockers: [], suggested: false }
          }
        };
      }
      blockers.push(`dnd_on_too_long(${Math.floor(minutes)}m) but notificationPolicyAccess missing`);
    }
  }

  return { blockers, overrideDecision: null };
}

function cooldownForPack(packId) {
  switch (String(packId)) {
    case 'office_focus_high_stress': return 45;
    case 'commute_alert': return 20;
    case 'recovery_mode': return 45;
    case 'home_evening': return 30;
    default: return 30;
  }
}

function parseIsoMs(value) {
  if (!value) return null;
  const parsed = new Date(String(value));
  const ms = parsed.getTime();
  return Number.isFinite(ms) ? ms : null;
}

function isRequiredLocationPack(context, packId) {
  const location = String(context.locationState ?? '').toLowerCase();
  if (packId === 'commute_alert' && location === 'commuting') return true;
  if (packId === 'office_focus_high_stress' && (location === 'office' || location === 'college')) return true;
  if (packId === 'home_evening' && (location === 'home' || location === 'relaxing')) return true;
  return false;
}

function matchingConditionalBlock(conditionalBlocks, packId, context, note) {
  const weekday = String(context.dayOfWeek ?? '').toLowerCase();
  for (const block of conditionalBlocks) {
    if (String(block.pack_id ?? '') !== packId) continue;
    if (block.weekday && String(block.weekday).toLowerCase() === weekday) {
      return `user blocked ${packId} on ${block.weekday}`;
    }
    if (block.meeting_keyword && note.includes(String(block.meeting_keyword).toLowerCase())) {
      return `user blocked ${packId} during ${block.meeting_keyword}`;
    }
    if (block.location_state && String(block.location_state).toLowerCase() === String(context.locationState).toLowerCase()) {
      return `user blocked ${packId} at ${block.location_state}`;
    }
  }
  return null;
}

function blocksQuietHours(context, decision) {
  const quietHours = context.memory?.profile?.preferences?.quiet_hours;
  if (!quietHours?.start || !quietHours?.end) return false;
  if (!decision.actions.some(action => action === 'SEND_DEPARTURE_ALERT' || action === 'BREAK_REMINDER')) return false;
  if (context.commutePlan?.leaveInMinutes != null && context.commutePlan.leaveInMinutes <= 10) return false;
  return isInQuietHours(context.hourOfDay, context.minuteOfHour, quietHours);
}

function isInQuietHours(hourOfDay, minuteOfHour, quietHours) {
  if (hourOfDay == null || minuteOfHour == null) return false;
  const nowMinutes = (hourOfDay * 60) + minuteOfHour;
  const start = parseClock(quietHours.start);
  const end = parseClock(quietHours.end);
  if (start == null || end == null) return false;
  if (start < end) return nowMinutes >= start && nowMinutes < end;
  return nowMinutes >= start || nowMinutes < end;
}

function parseClock(value) {
  const match = String(value).match(/^(\d{1,2}):(\d{2})$/);
  if (!match) return null;
  return (Number(match[1]) * 60) + Number(match[2]);
}
