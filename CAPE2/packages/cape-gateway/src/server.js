const http = require('http');
const path = require('path');
const { geocodeAddress, searchPlaces } = require('./commute-planner');
const { createOpenClawOrchestrator } = require('./openclaw-orchestrator');

const DEFAULT_PORT = Number(process.env.CAPE_GATEWAY_PORT ?? 8787);
const DEFAULT_HOST = process.env.CAPE_GATEWAY_HOST ?? '127.0.0.1';
const MEMORY_DIR = process.env.OPENCLAW_CAPE_MEMORY_DIR ?? path.resolve(__dirname, '../../../openclaw/runtime');
const OPENCLAW_MEMORY_DIR = process.env.OPENCLAW_CAPE_PROFILE_DIR ?? path.resolve(__dirname, '../../../openclaw/memory');

function createServer(options = {}) {
  const runtimeDir = options.runtimeDir ?? MEMORY_DIR;
  const memoryDir = options.memoryDir ?? OPENCLAW_MEMORY_DIR;
  const openclaw = createOpenClawOrchestrator({
    runtimeDir,
    memoryDir,
    reasoningNoteBuilder: options.reasoningNoteBuilder,
    now: options.now,
    commuteAgentOptions: options.commuteAgentOptions
  });

  return http.createServer(async (req, res) => {
    try {
      if (req.method === 'GET' && req.url === '/health') {
        return sendJson(res, 200, {
          ok: true,
          service: 'cape-gateway',
          version: '0.1.0'
        });
      }

      if (req.method === 'POST' && req.url === '/v1/context/decision') {
        const body = await readJson(req);
        return sendJson(res, 200, await openclaw.runContextDecision(body));
      }

      if (req.method === 'POST' && req.url === '/v1/feedback') {
        const body = await readJson(req);
        const result = await openclaw.recordFeedback(body);
        return sendJson(res, 200, {
          ok: true,
          message: 'feedback_recorded',
          learning: result.learning,
          openclaw: result.openclaw
        });
      }

      if (req.method === 'POST' && req.url === '/v1/maps/geocode') {
        const body = await readJson(req);
        if (!body.query) throw new Error('query_required');
        if (!process.env.GOOGLE_MAPS_API_KEY) throw new Error('google_maps_api_key_missing');
        const place = await geocodeAddress(body.query, process.env.GOOGLE_MAPS_API_KEY);
        return sendJson(res, 200, { place });
      }

      if (req.method === 'POST' && req.url === '/v1/maps/search') {
        const body = await readJson(req);
        if (!body.query) throw new Error('query_required');
        if (!process.env.GOOGLE_MAPS_API_KEY) throw new Error('google_maps_api_key_missing');
        const places = await searchPlaces(body.query, process.env.GOOGLE_MAPS_API_KEY);
        return sendJson(res, 200, { places });
      }

      return sendJson(res, 404, { error: 'not_found' });
    } catch (error) {
      return sendJson(res, 400, {
        error: 'bad_request',
        message: error.message
      });
    }
  });
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', chunk => {
      raw += chunk;
      if (raw.length > 1_000_000) {
        reject(new Error('request_too_large'));
        req.destroy();
      }
    });
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        reject(new Error('invalid_json'));
      }
    });
    req.on('error', reject);
  });
}

function sendJson(res, status, body) {
  const payload = JSON.stringify(body, null, 2);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(payload)
  });
  res.end(payload);
}

if (require.main === module) {
  const server = createServer();
  server.listen(DEFAULT_PORT, DEFAULT_HOST, () => {
    console.log(`CAPE gateway listening on http://${DEFAULT_HOST}:${DEFAULT_PORT}`);
    console.log(`CAPE memory writing to ${MEMORY_DIR}`);
  });
}

module.exports = {
  createServer
};
