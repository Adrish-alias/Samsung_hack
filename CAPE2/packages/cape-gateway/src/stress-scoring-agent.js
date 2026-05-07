const { calculateStressScore } = require('../../cape-core/src');

function createStressScoringAgent() {
  function score(context) {
    const raw = calculateStressScore(context);
    const stress = smoothStress(raw, context);
    return {
      ...stress,
      summary: buildSummary(stress)
    };
  }

  return {
    score
  };
}

function smoothStress(raw, context) {
  const previous = Number(context.memory?.routine?.runtime_state?.last_stress_score);
  if (!Number.isFinite(previous)) {
    return { ...raw, rawScore: raw.score, smoothing: 'first_sample' };
  }
  const delta = raw.score - previous;
  const maxRise = 12;
  const maxFall = 18;
  const smoothed = delta > 0
    ? previous + Math.min(delta, maxRise)
    : previous + Math.max(delta, -maxFall);
  const score = Math.round((0.65 * smoothed) + (0.35 * raw.score));
  const reasons = [...raw.reasons];
  if (Math.abs(raw.score - score) >= 5) reasons.push('stress_smoothing');
  return {
    ...raw,
    score: clamp(score, 0, 100),
    level: levelFor(clamp(score, 0, 100)),
    rawScore: raw.score,
    reasons,
    smoothing: `previous ${previous}, raw ${raw.score}, smoothed ${clamp(score, 0, 100)}`
  };
}

function buildSummary(stress) {
  if (stress.reasons.length === 0) {
    return `Stress ${stress.score}/100 is ${stress.level} with no dominant risk signals.`;
  }
  const raw = stress.rawScore != null && stress.rawScore !== stress.score ? ` Raw signal was ${stress.rawScore}/100 and was smoothed to avoid a short-lived spike.` : '';
  return `Stress ${stress.score}/100 is ${stress.level} because of ${stress.reasons.join(', ')}.${raw}`;
}

function levelFor(score) {
  if (score >= 75) return 'critical';
  if (score >= 60) return 'high';
  if (score >= 35) return 'medium';
  return 'low';
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

module.exports = {
  createStressScoringAgent
};
