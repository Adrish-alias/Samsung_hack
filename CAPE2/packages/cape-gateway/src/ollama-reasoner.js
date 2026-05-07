const http = require('http');

async function buildReasoningNote(context, decisionPreview) {
  const baseUrl = process.env.OLLAMA_BASE_URL ?? 'http://127.0.0.1:11434';
  const preferredModel = process.env.OLLAMA_MODEL ?? 'llama3.1:8b';
  const prompt = [
    'You are CAPE, a privacy-aware Android context agent.',
    'Return one short sentence explaining the decision. No markdown.',
    `Context: ${JSON.stringify(redactContext(context))}`,
    `Decision preview: ${JSON.stringify({
      type: decisionPreview.type,
      packId: decisionPreview.packId,
      stress: decisionPreview.stress,
      actions: decisionPreview.actions
    })}`
  ].join('\n');

  try {
    let model = preferredModel;
    let response = await tryGenerate(baseUrl, model, prompt);
    if (response.statusCode === 404) {
      model = await firstAvailableModel(baseUrl) ?? preferredModel;
      response = await tryGenerate(baseUrl, model, prompt);
    }
    if (response.statusCode < 200 || response.statusCode > 299) {
      throw new Error(`HTTP ${response.statusCode}`);
    }
    return String(response.body.response || '').trim().replace(/\s+/g, ' ').slice(0, 240) || null;
  } catch (error) {
    return `Ollama reasoning unavailable: ${error.message}`;
  }
}

async function tryGenerate(baseUrl, model, prompt) {
  return postJson(`${baseUrl}/api/generate`, {
    model,
    prompt,
    stream: false,
    options: {
      temperature: 0.2,
      num_predict: 80
    }
  }, Number(process.env.OLLAMA_TIMEOUT_MS ?? 30000));
}

async function firstAvailableModel(baseUrl) {
  try {
    const response = await getJson(`${baseUrl}/api/tags`, 3000);
    return response.body.models?.[0]?.name ?? response.body.models?.[0]?.model ?? null;
  } catch {
    return null;
  }
}

function getJson(urlString, timeoutMs) {
  const url = new URL(urlString);
  return new Promise((resolve, reject) => {
    const req = http.request({
      hostname: url.hostname,
      port: url.port || 80,
      path: url.pathname,
      method: 'GET',
      timeout: timeoutMs
    }, res => {
      let raw = '';
      res.on('data', chunk => raw += chunk);
      res.on('end', () => {
        try {
          resolve({ statusCode: res.statusCode, body: JSON.parse(raw) });
        } catch (error) {
          reject(error);
        }
      });
    });
    req.on('timeout', () => req.destroy(new Error('ollama_timeout')));
    req.on('error', reject);
    req.end();
  });
}

function redactContext(context) {
  return {
    locationState: context.locationState,
    sleepDebtMinutes: context.sleepDebtMinutes,
    meetingLoadToday: context.meetingLoadToday,
    commuteDelayMinutes: context.commuteDelayMinutes,
    screenTimeLast2hMinutes: context.screenTimeLast2hMinutes,
    nextMeetingMinutes: context.nextMeetingMinutes,
    hasDestination: Boolean(context.nextMeetingLocation || context.destination),
    commutePlan: context.commutePlan
  };
}

function postJson(urlString, body, timeoutMs) {
  const url = new URL(urlString);
  const payload = JSON.stringify(body);
  return new Promise((resolve, reject) => {
    const req = http.request({
      hostname: url.hostname,
      port: url.port || 80,
      path: url.pathname,
      method: 'POST',
      timeout: timeoutMs,
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload)
      }
    }, res => {
      let raw = '';
      res.on('data', chunk => raw += chunk);
      res.on('end', () => {
        try {
          resolve({ statusCode: res.statusCode, body: JSON.parse(raw) });
        } catch (error) {
          reject(error);
        }
      });
    });
    req.on('timeout', () => {
      req.destroy(new Error('ollama_timeout'));
    });
    req.on('error', reject);
    req.write(payload);
    req.end();
  });
}

module.exports = {
  buildReasoningNote
};
