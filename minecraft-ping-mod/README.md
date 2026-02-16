# simpleMultiplayerPing (Fabric Client Mod)

A ping mod that allows players to create groups and share temporary pings on multiplayer servers by clicking the mouse wheel or holding it to open a ping wheel with advanced ping options.

Only client-side installation is required, which makes this mod usable on minigame servers like Hypixel.
The relay host can be self-hosted or you can use the public host for free.

## Features

- Client-side only mod (no server plugin required)
- Temporary world pings synced via relay
- Default auto-party code `1` on server join
- Optional party menu (`P`) for custom groups on the same server
- Ping wheel (mouse wheel hold) with 3 ping types:
	- `Normal` (mouse wheel click)
	- `Warning` (mouse wheel hold + pull up)
	- `Move` (mouse wheel hold + pull down)
- Configurable settings (via Mod Menu):
	- Ping color
	- Ping size
	- Ping duration
	- Per-player colors for other players
	- Show/hide sender name under ping
	- Relay host override
	- Reset to defaults

## Requirements

- Java 21
- Fabric Loader + Fabric API (Minecraft 1.21.11)
- Optional but recommended: Mod Menu (for in-game settings)

## Build

```bash
./gradlew build
```

If the wrapper is missing in your local clone, run with local Gradle:

```bash
gradle build
```

Output JAR:

`build/libs/minecraft-ping-mod-<version>.jar`

## Install Mod

### Linux/macOS

```bash
chmod +x install-mod.sh
./install-mod.sh
```

Optional explicit JAR:

```bash
./install-mod.sh /path/to/minecraft-ping-mod-0.1.0.jar
```

### Windows (PowerShell)

```powershell
./install-mod.ps1
```

Optional explicit JAR:

```powershell
./install-mod.ps1 -JarPath ".\build\libs\minecraft-ping-mod-0.1.0.jar"
```

## Relay Host

Default relay host:

`ws://158.180.50.88:8787`

You can override this in Mod Menu settings (`Relay Host`) to use your own server.

## Usage

1. Join any multiplayer server.
2. You are automatically in default party code `1` for that server.
3. Share pings with mouse wheel.
4. Use `P` only if you and others want different groups on the same server.

Pings are isolated by both `party` and `serverId` to prevent cross-server leaks.

## Screenshots

Ping wheel selection + warning/move ping examples (color is configurable):

![Ping wheel and ping types](https://cdn.modrinth.com/data/cached_images/90ff7de2b698e5e33a225e972cc03844a2dfd48b_0.webp)

Party menu (default party is code `1`):

![Party menu](https://cdn.modrinth.com/data/cached_images/d5ea56c6640b7feb8a4d0e47abfb9f17c089d3dc_0.webp)

## Modrinth Description

Use the ready-to-paste text from:

`MODRINTH_DESCRIPTION.md`
