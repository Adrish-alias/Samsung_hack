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
      notes: []
    };

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
      pushUnique(routine.overrides.blocked_keywords, keyword);
      learned.push(`keyword override ${keyword}`);
      store.appendSoulRule(`Avoid automation when context suggests ${keyword}.`);
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
  const match = note.match(/during\s+([a-z0-9:_\-/ ]{2,40})/i);
  if (!match) return null;
  return match[1].trim().replace(/\.$/, '');
}

function pushUnique(list, value) {
  if (!Array.isArray(list)) return;
  if (!list.includes(value)) list.push(value);
}

module.exports = {
  createFeedbackLearningAgent
};
