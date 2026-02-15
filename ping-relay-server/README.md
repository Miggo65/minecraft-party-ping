# Ping Relay Server

Ein minimaler WebSocket-Relay für die clientseitige Minecraft-Ping-Mod.

## Install auf Linux-Server (systemd)

```bash
chmod +x install.sh
sudo bash install.sh
```

Optionaler Port:

```bash
sudo PORT=8787 bash install.sh
```

## Start

```bash
npm install
npm start
```

Optionaler Port:

```bash
PORT=8787 npm start
```

## Verhalten

- Der Server speichert pro Verbindung: `party`, `serverId`, `player`
- `ping`-Events werden **nur** an Clients mit identischer `party` und identischem `serverId` gesendet
- Der Sender bekommt den Ping nicht zurück (Client zeigt lokalen Ping direkt an)
