function createFeedbackLearningAgent(store) {
  function record(feedback) {
    const snapshot = store.readAll();
    const profile = snapshot.profile;
    const routine = snapshot.routine;
    const packId = feedback.packId ?? 'unknown';
    const signal = feedback.signal ?? 'unknown';
    const note = String(feedback.note ?? '').trim();
    const lowerNote = note.toLowerCase();

    if (!routine.pack_feedback?.[packId]) {
      routine.pack_feedback ??= {};
      routine.pack_feedback[packId] = { accepted: 0, rejected: 0 };
    }

    if (signal === 'accepted') {
      routine.pack_feedback[packId].accepted += 1;
    }

    if (signal === 'rejected') {
      routine.pack_feedback[packId].rejected += 1;
    }

    routine.overrides ??= {
      blocked_packs: [],
      blocked_keywords: [],
      conditional_blocks: [],
      notes: []
    };
    routine.overrides.blocked_packs ??= [];
    routine.overrides.blocked_keywords ??= [];
    routine.overrides.conditional_blocks ??= [];
    routine.overrides.notes ??= [];

    const learned = [];

    if (signal === 'rejected' && note) {
      routine.overrides.notes.push(note);
    }

    if (/don't do this|do not do this|never do this/.test(lowerNote)) {
      pushUnique(routine.overrides.blocked_packs, packId);
      learned.push(`blocked pack ${packId}`);
      store.appendSoulRule(`Do not auto-apply ${packId} after the user explicitly rejected it.`);
    }

    const keyword = extractKeyword(lowerNote);
    if (keyword) {
      pushConditionalBlock(routine.overrides.conditional_blocks, {
        pack_id: packId,
        meeting_keyword: keyword
      });
      learned.push(`meeting keyword override ${keyword}`);
      store.appendSoulRule(`Avoid ${packId} when the meeting context suggests ${keyword}.`);
    }

    const weekday = extractWeekday(lowerNote);
    if (weekday) {
      pushConditionalBlock(routine.overrides.conditional_blocks, {
        pack_id: packId,
        weekday
      });
      learned.push(`weekday override ${weekday}`);
      store.appendSoulRule(`Avoid ${packId} on ${weekday}.`);
    }

    const locationState = extractLocationState(lowerNote);
    if (locationState) {
      pushConditionalBlock(routine.overrides.conditional_blocks, {
        pack_id: packId,
        location_state: locationState
      });
      learned.push(`location override ${locationState}`);
      store.appendSoulRule(`Avoid ${packId} when the user is ${locationState}.`);
    }

    if (!keyword && !weekday && !locationState && /1:1|meeting|call|class|lecture/.test(lowerNote)) {
      const generalKeyword = extractGeneralKeyword(lowerNote);
      if (generalKeyword) {
        pushUnique(routine.overrides.blocked_keywords, generalKeyword);
        learned.push(`keyword override ${generalKeyword}`);
        store.appendSoulRule(`Avoid automation when context suggests ${generalKeyword}.`);
      }
    }

    store.writeProfile(profile);
    store.writeRoutine(routine);

    return {
      ok: true,
      updated: {
        packId,
        signal,
        learned
      }
    };
  }

  return {
    record
  };
}

function extractKeyword(note) {
  const match = note.match(/during\s+([a-z0-9:_\-/ ]{2,40}?)(?:\s+on\s+[a-z]+s?|\s*$|\.)/i);
  if (!match) return null;
  return match[1].trim().replace(/\.$/, '');
}

function extractWeekday(note) {
  const weekdays = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'];
  for (const weekday of weekdays) {
    if (note.includes(weekday) || note.includes(`${weekday}s`)) return weekday;
  }
  return null;
}

function extractLocationState(note) {
  if (note.includes('at home')) return 'home';
  if (note.includes('at office')) return 'office';
  if (note.includes('while commuting') || note.includes('during commute')) return 'commuting';
  return null;
}

function extractGeneralKeyword(note) {
  const match = note.match(/\b(1:1s?|meetings?|calls?|classes?|lectures?)\b/i);
  return match?.[1]?.toLowerCase() ?? null;
}

function pushUnique(list, value) {
  if (!Array.isArray(list)) return;
  if (!list.includes(value)) list.push(value);
}

function pushConditionalBlock(list, block) {
  if (!Array.isArray(list)) return;
  const exists = list.some(existing =>
    String(existing.pack_id ?? '') === String(block.pack_id ?? '') &&
    String(existing.weekday ?? '') === String(block.weekday ?? '') &&
    String(existing.meeting_keyword ?? '') === String(block.meeting_keyword ?? '') &&
    String(existing.location_state ?? '') === String(block.location_state ?? '')
  );
  if (!exists) list.push(block);
}

module.exports = {
  createFeedbackLearningAgent
};
