const { calculateStressScore } = require('../../cape-core/src');

function createStressScoringAgent() {
  function score(context) {
    const stress = calculateStressScore(context);
    return {
      ...stress,
      summary: buildSummary(stress)
    };
  }

  return {
    score
  };
}

function buildSummary(stress) {
  if (stress.reasons.length === 0) {
    return `Stress ${stress.score}/100 is ${stress.level} with no dominant risk signals.`;
  }
  return `Stress ${stress.score}/100 is ${stress.level} because of ${stress.reasons.join(', ')}.`;
}

module.exports = {
  createStressScoringAgent
};
