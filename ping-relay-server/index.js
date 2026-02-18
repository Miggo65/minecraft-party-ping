import { WebSocketServer } from 'ws';

const PORT = Number(process.env.PORT || 8787);
const MAX_MESSAGE_BYTES = readEnvInt('MAX_MESSAGE_BYTES', 4096, 512, 65536);
const MAX_TEXT_LENGTH = readEnvInt('MAX_TEXT_LENGTH', 96, 16, 512);
const MAX_PLAYER_LENGTH = readEnvInt('MAX_PLAYER_LENGTH', 32, 3, 64);
const MAX_ABS_COORDINATE = readEnvInt('MAX_ABS_COORDINATE', 30_000_000, 1024, 50_000_000);
const MIN_Y = readEnvInt('MIN_Y', -2048, -8192, 0);
const MAX_Y = readEnvInt('MAX_Y', 4096, 256, 8192);
const MAX_INVALID_MESSAGES = readEnvInt('MAX_INVALID_MESSAGES', 8, 1, 100);
const PING_RATE_WINDOW_MS = readEnvInt('PING_RATE_WINDOW_MS', 1000, 100, 10_000);
const PING_RATE_MAX = readEnvInt('PING_RATE_MAX', 12, 1, 120);

const wss = new WebSocketServer({
  port: PORT,
  maxPayload: MAX_MESSAGE_BYTES,
  perMessageDeflate: false
});

function readEnvInt(name, fallback, min, max) {
  const raw = process.env[name];
  if (raw === undefined) {
    return fallback;
  }

  const parsed = Number(raw);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }

  const integer = Math.trunc(parsed);
  if (integer < min || integer > max) {
    return fallback;
  }
  return integer;
}

function safeParse(raw) {
  if (!raw) {
    return null;
  }
  const asString = raw.toString();
  if (asString.length === 0 || asString.length > MAX_MESSAGE_BYTES) {
    return null;
  }
  try {
    return JSON.parse(asString);
  } catch {
    return null;
  }
}

function isString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function normalizeParty(value) {
  return value.trim().toUpperCase();
}

function normalizeServerId(value) {
  return value.trim().toLowerCase();
}

function cleanText(value, maxLength) {
  if (!isString(value)) {
    return null;
  }
  const normalized = value.trim();
  if (normalized.length > maxLength) {
    return null;
  }
  return normalized;
}

function cleanPlayer(value) {
  const player = cleanText(value, MAX_PLAYER_LENGTH);
  if (!player) {
    return 'unknown';
  }
  return player.replace(/[^a-zA-Z0-9_\-]/g, '').slice(0, MAX_PLAYER_LENGTH) || 'unknown';
}

function isValidIdentifier(value, maxLength = MAX_TEXT_LENGTH) {
  const normalized = cleanText(value, maxLength);
  if (!normalized) {
    return false;
  }
  return /^[a-z0-9._:/-]+$/i.test(normalized);
}

function isValidDimension(value) {
  const normalized = cleanText(value, MAX_TEXT_LENGTH);
  if (!normalized) {
    return false;
  }
  return /^[a-z0-9._-]+:[a-z0-9._/-]+$/i.test(normalized);
}

function readFiniteNumber(value) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return null;
  }
  return value;
}

function isPositionInBounds(x, y, z) {
  return Math.abs(x) <= MAX_ABS_COORDINATE
    && Math.abs(z) <= MAX_ABS_COORDINATE
    && y >= MIN_Y
    && y <= MAX_Y;
}

function registerInvalid(ws) {
  ws.invalidCount += 1;
  if (ws.invalidCount >= MAX_INVALID_MESSAGES) {
    ws.close(1008, 'too-many-invalid-requests');
  }
}

function canSendPing(ws) {
  const now = Date.now();
  if (now - ws.rateWindowStart >= PING_RATE_WINDOW_MS) {
    ws.rateWindowStart = now;
    ws.rateCount = 0;
  }

  ws.rateCount += 1;
  return ws.rateCount <= PING_RATE_MAX;
}

function normalizePingType(value) {
  if (!isString(value)) {
    return 'normal';
  }
  const normalized = value.trim().toLowerCase();
  if (normalized === 'warning' || normalized === 'vorsicht' || normalized === 'achtung') {
    return 'warning';
  }
  if (normalized === 'go' || normalized === 'move' || normalized === 'gehen') {
    return 'go';
  }
  return 'normal';
}

function broadcastPing(senderWs, payload) {
  for (const client of wss.clients) {
    if (client === senderWs) {
      continue;
    }
    if (client.readyState !== 1) {
      continue;
    }
    if (client.party !== payload.party) {
      continue;
    }
    if (client.serverId !== payload.serverId) {
      continue;
    }
    client.send(JSON.stringify(payload));
  }
}

wss.on('connection', (ws) => {
  ws.party = '';
  ws.serverId = '';
  ws.player = 'unknown';
  ws.invalidCount = 0;
  ws.rateWindowStart = Date.now();
  ws.rateCount = 0;
  console.log('[relay] client connected');

  ws.on('message', (raw) => {
    const msg = safeParse(raw);
    if (!msg || !isString(msg.type)) {
      registerInvalid(ws);
      return;
    }

    if (msg.type === 'join') {
      if (!isValidIdentifier(msg.party, MAX_TEXT_LENGTH) || !isValidIdentifier(msg.serverId, MAX_TEXT_LENGTH)) {
        registerInvalid(ws);
        return;
      }
      ws.party = normalizeParty(msg.party);
      ws.serverId = normalizeServerId(msg.serverId);
      ws.player = cleanPlayer(msg.player);
      console.log(`[relay] join player=${ws.player} party=${ws.party} server=${ws.serverId}`);
      return;
    }

    if (msg.type === 'leave') {
      console.log(`[relay] leave player=${ws.player} party=${ws.party} server=${ws.serverId}`);
      ws.party = '';
      ws.serverId = '';
      return;
    }

    if (msg.type === 'ping') {
      if (!canSendPing(ws)) {
        registerInvalid(ws);
        return;
      }
      if (!isValidIdentifier(msg.party, MAX_TEXT_LENGTH)
        || !isValidIdentifier(msg.serverId, MAX_TEXT_LENGTH)
        || !isValidDimension(msg.dimension)) {
        registerInvalid(ws);
        return;
      }

      const x = readFiniteNumber(msg.x);
      const y = readFiniteNumber(msg.y);
      const z = readFiniteNumber(msg.z);
      if (x === null || y === null || z === null || !isPositionInBounds(x, y, z)) {
        registerInvalid(ws);
        return;
      }

      const incomingParty = normalizeParty(msg.party);
      const incomingServerId = normalizeServerId(msg.serverId);
      if (!ws.party || !ws.serverId) {
        registerInvalid(ws);
        return;
      }
      if (incomingParty !== ws.party || incomingServerId !== ws.serverId) {
        registerInvalid(ws);
        return;
      }

      const payload = {
        type: 'ping',
        party: ws.party,
        serverId: ws.serverId,
        player: ws.player,
        dimension: msg.dimension,
        x,
        y,
        z,
        pingType: normalizePingType(msg.pingType),
        t: Date.now()
      };

      console.log(`[relay] ping from=${payload.player} party=${payload.party} server=${payload.serverId} type=${payload.pingType} pos=(${Math.round(payload.x)}, ${Math.round(payload.y)}, ${Math.round(payload.z)}) dim=${payload.dimension}`);

      broadcastPing(ws, payload);
    }
  });

  ws.on('error', () => {
  });

  ws.on('close', () => {
    console.log(`[relay] client disconnected player=${ws.player}`);
  });
});

console.log(`Ping relay listening on ws://0.0.0.0:${PORT}`);
