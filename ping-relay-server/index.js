import { WebSocketServer } from 'ws';

const PORT = Number(process.env.PORT || 8787);
const wss = new WebSocketServer({ port: PORT });

function safeParse(raw) {
  try {
    return JSON.parse(raw.toString());
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
  console.log('[relay] client connected');

  ws.on('message', (raw) => {
    const msg = safeParse(raw);
    if (!msg || !isString(msg.type)) {
      return;
    }

    if (msg.type === 'join') {
      if (!isString(msg.party) || !isString(msg.serverId)) {
        return;
      }
      ws.party = normalizeParty(msg.party);
      ws.serverId = normalizeServerId(msg.serverId);
      ws.player = isString(msg.player) ? msg.player.trim() : 'unknown';
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
      if (!isString(msg.party) || !isString(msg.serverId) || !isString(msg.dimension)) {
        return;
      }
      if (typeof msg.x !== 'number' || typeof msg.y !== 'number' || typeof msg.z !== 'number') {
        return;
      }

      const incomingParty = normalizeParty(msg.party);
      const incomingServerId = normalizeServerId(msg.serverId);
      if (!ws.party || !ws.serverId) {
        return;
      }
      if (incomingParty !== ws.party || incomingServerId !== ws.serverId) {
        return;
      }

      const payload = {
        type: 'ping',
        party: ws.party,
        serverId: ws.serverId,
        player: ws.player,
        dimension: msg.dimension,
        x: msg.x,
        y: msg.y,
        z: msg.z,
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
