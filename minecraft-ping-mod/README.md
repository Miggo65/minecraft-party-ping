# Minecraft Ping Mod (Clientside)

Fabric-Clientmod für 1.21.11:

- Mausrad (`Button 2`) setzt Ping auf den anvisierten Block
- Ping bleibt 30 Sekunden sichtbar
- Distanz wird unter dem Ping-Icon angezeigt
- `P` öffnet Party-Menü (Party erstellen / beitreten / verlassen)
- Sync läuft über separaten WebSocket-Relay-Server

## Voraussetzungen

- Java 21
- Fabric Loader + Fabric API (Client, 1.21.11)
- Laufender Relay-Server (`ping-relay-server`)

## Build

```bash
./gradlew build
```

Das JAR liegt danach unter `build/libs/`.

## Mod schnell installieren

```bash
chmod +x install-mod.sh
./install-mod.sh
```

Optional mit explizitem JAR:

```bash
./install-mod.sh /pfad/zur/minecraft-ping-mod-0.1.0.jar
```

### Windows (PowerShell)

```powershell
./install-mod.ps1
```

Optional mit explizitem JAR:

```powershell
./install-mod.ps1 -JarPath ".\\build\\libs\\minecraft-ping-mod-0.1.0.jar"
```

## Config

Beim ersten Start wird erzeugt:

`config/mcping-client.json`

Beispiel:

```json
{
  "relayUrl": "ws://127.0.0.1:8787"
}
```

## Nutzung

1. Relay starten
2. Minecraft mit Mod starten
3. `P` drücken, Party erstellen oder Party-Code eingeben
4. Mausrad auf Block drücken, um Ping zu senden

Wichtig: Pings werden nur zwischen Spielern mit **gleicher Party** und **gleichem Server** verteilt.
