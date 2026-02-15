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

  ws.on('message', (raw) => {
    const msg = safeParse(raw);
    if (!msg || !isString(msg.type)) {
      return;
    }

    if (msg.type === 'join') {
      if (!isString(msg.party) || !isString(msg.serverId)) {
        return;
      }
      ws.party = msg.party.trim().toUpperCase();
      ws.serverId = msg.serverId.trim().toLowerCase();
      ws.player = isString(msg.player) ? msg.player.trim() : 'unknown';
      return;
    }

    if (msg.type === 'leave') {
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

      const payload = {
        type: 'ping',
        party: msg.party.trim().toUpperCase(),
        serverId: msg.serverId.trim().toLowerCase(),
        player: isString(msg.player) ? msg.player.trim() : ws.player,
        dimension: msg.dimension,
        x: msg.x,
        y: msg.y,
        z: msg.z,
        t: Date.now()
      };

      broadcastPing(ws, payload);
    }
  });

  ws.on('error', () => {
  });
});

console.log(`Ping relay listening on ws://0.0.0.0:${PORT}`);
