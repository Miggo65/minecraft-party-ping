# Ping Relay Server

Minimal WebSocket relay for `simpleMultiplayerPing`.

## Requirements

- Linux server (recommended)
- Node.js 20+
- Open TCP port (default `8787`)

## Install as systemd service (recommended)

```bash
chmod +x install.sh
sudo bash install.sh
```

Optional custom port:

```bash
sudo PORT=8787 bash install.sh
```

Service name: `ping-relay`

Useful commands:

```bash
sudo systemctl status ping-relay
sudo journalctl -u ping-relay -f
sudo systemctl restart ping-relay
```

## Manual run (dev/test)

```bash
npm install
npm start
```

Optional port:

```bash
PORT=8787 npm start
```

## Relay behavior

- Tracks per connection: `party`, `serverId`, `player`
- Broadcasts `ping` only to clients with matching `party` **and** matching `serverId`
- Sender does not receive their own ping back
- Includes `pingType` (`normal`, `warning`, `go`) in forwarded payloads

This prevents cross-server ping leaks and keeps party groups isolated.
