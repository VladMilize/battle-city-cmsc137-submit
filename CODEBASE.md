# Battle City — Codebase Guide

## What this project is

Battle City is a two-team tank game where Red and Blue players fight to destroy each other's Eagle base or wipe out all enemy tanks. Up to two players per team can play together over a local network using Java's built-in TCP sockets. One machine runs the server (which owns all the game logic), and each player connects as a client that sends key presses and renders whatever the server sends back. There is also a local two-player mode where both players share one keyboard.

## Folder structure

**src/** — all the Java source code, split into packages by responsibility.

- **src/core/** — the game engine parts: the main canvas, the game loop, constants, collision detection, and keyboard input.
- **src/entity/** — the game objects that move around: tanks (Player), bullets (Projectile), and Eagle bases.
- **src/map/** — the tile system: what each tile type looks like and how the map is loaded from a text file.
- **src/ui/** — everything the player reads on screen: the HUD bar, the main menu, and the pause menu.
- **src/network/** — all the networking code: the server, client, per-player connection handlers, message format, and protocol helpers.
- **src/chat/** — the in-game chat overlay that shows recent messages during network play.
- **src/Main.java** — the single entry point that opens the window and starts everything.

**res/** — image and map assets. Tank sprites live in `res/player/`, tile images in `res/tiles/`, and the map layout in `res/maps/map1.txt`. These get copied into `out/` at build time so the game can find them on the classpath.

**out/** — compiled `.class` files and copied resources produced by `run.sh`. You should never edit anything here; it gets overwritten every build.

---

## File-by-file guide

### CollisionChecker.java
**Where it lives:** src/core/

**What it does:** Decides whether a moving object is about to hit a wall or another tank. Before a tank or bullet actually moves, this file looks at the tiles ahead and checks for overlap with other tanks. If something is in the way, it raises a flag on the entity so the caller knows not to move it.

**Talks to:** GamePanel.java (gives it access to the map and tank list), Entity.java (sets the `collisionOn` flag), TileManager.java (reads the tile-number grid to check wall types)

---

### Constants.java
**Where it lives:** src/core/

**What it does:** Stores every fixed number the game uses in one place — tile size (48 px), screen dimensions (16 × 12 tiles), target frame rate (60 FPS), and the maximum number of players (4). Any part of the code that needs one of these numbers reads it from here instead of writing it directly, so changing one value here updates the whole game.

**Talks to:** GamePanel.java, GameServer.java, ServerGameState.java, and others read from this file.

---

### GamePanel.java
**Where it lives:** src/core/

**What it does:** The main game canvas and the heart of the client. It runs the fixed-timestep game loop, reads keyboard input, and draws all game objects. In local mode it updates tanks and bullets directly. In network mode it sends the held keys to the server each frame and draws the game from the JSON state the server broadcasts. It also manages the chat input bar, the pause state, and all transitions between menu, playing, paused, and game-over screens.

**Talks to:** TileManager.java (draws the map), Player.java / Eagle.java / Projectile.java (updates and draws local entities), GameClient.java (sends input and reads server state in network mode), MenuScreen.java / HUD.java / PauseMenu.java (draws the UI layers on top)

---

### GameState.java
**Where it lives:** src/core/

**What it does:** A four-value list — MENU, PLAYING, PAUSED, GAME_OVER — that describes what phase the game is in. GamePanel keeps track of the current state and uses it to decide what to update and draw each frame.

**Talks to:** GamePanel.java (reads and writes the state), MenuScreen.java, HUD.java, PauseMenu.java (check the state to decide what to show)

---

### KeyHandler.java
**Where it lives:** src/core/

**What it does:** Listens for keyboard events and keeps a boolean flag for every key the game cares about. When a key is pressed the flag goes `true`; when released it goes `false`. Other parts of the code just read these flags each frame instead of dealing with keyboard events directly.

**Talks to:** GamePanel.java (registers it as a key listener and reads menu/action keys), Player.java (reads the movement and fire keys each update tick)

---

### Eagle.java
**Where it lives:** src/entity/

**What it does:** Represents each team's base on the map. It tracks the eagle's tile position and whether it is still alive. When drawn, it shows the eagle image if alive or a black tile with a red X if it has been destroyed. GamePanel checks whether any eagle is inactive to trigger a win.

**Talks to:** GamePanel.java (holds a list of eagles and calls `draw` each frame), Projectile.java (bullets call back through GamePanel to destroy the eagle on a direct hit), TileManager.java (the eagle's map tile is cleared to EMPTY when it is destroyed)

---

### Entity.java
**Where it lives:** src/entity/

**What it does:** The base class that all moving game objects share. It holds the fields every entity needs: position (x, y), speed, direction, sprite images for all four directions, the collision rectangle, and an `active` flag. Player, Projectile, and Eagle all extend this so they inherit these basics without repeating them.

**Talks to:** Player.java, Projectile.java, Eagle.java (all extend it), CollisionChecker.java (reads position, solidArea, direction, speed, and sets `collisionOn`)

---

### Player.java
**Where it lives:** src/entity/

**What it does:** Handles one tank's movement, firing, and respawning. Each frame it checks which keys are held, asks CollisionChecker if the intended direction is clear, moves the tank if it is, and fires a bullet when the shoot key is pressed and the cooldown has expired. When hit by a bullet, the tank becomes inactive for a brief countdown before reappearing at its spawn point. It also draws the tank sprite with a colored border showing which team it belongs to.

**Talks to:** GamePanel.java (the game loop calls `update` and `draw`), KeyHandler.java (reads which movement and fire keys are held), CollisionChecker.java (checks walls and other tanks before moving), Projectile.java (creates a new bullet when firing)

---

### Projectile.java
**Where it lives:** src/entity/

**What it does:** Moves a bullet across the map each frame and checks what it runs into. If the bullet leaves the screen, hits a wall, hits an enemy tank, or reaches an eagle tile, it stops and triggers the right effect — removing a brick wall, reducing a player's lives, or destroying the eagle.

**Talks to:** GamePanel.java (holds the bullet list and calls `update` and `draw`), TileManager.java (calls `tryDestroyAt` to remove a BRICK tile on impact), Player.java (calls `hit()` when a tank is struck), GamePanel.java via `tryDestroyEagleAt` (destroys the eagle when a bullet hits its tile)

---

### Tile.java
**Where it lives:** src/map/

**What it does:** Describes one tile type — its image, whether movement through it is blocked (`collision`), whether bullets can break it (`destructible`), and its category (EMPTY, BRICK, STEEL, WATER, GRASS, or EAGLE). TileManager keeps an array of six Tile objects and looks up the right one using the number stored in the map grid.

**Talks to:** TileManager.java (creates and reads an array of Tile objects), CollisionChecker.java (indirectly, through TileManager's tile array)

---

### TileManager.java
**Where it lives:** src/map/

**What it does:** Loads the map from a text file into a 16 × 12 grid of tile-type numbers, draws all non-grass tiles each frame, then draws grass tiles again after players so tanks appear to move under the grass canopy. It also handles brick destruction: when a bullet hits a BRICK tile, this file sets that cell to 0 (EMPTY).

**Talks to:** GamePanel.java (is given a reference to GamePanel at startup; GamePanel calls `draw`, `drawGrassOverlay`, and `loadMap`), Tile.java (reads tile descriptors from the array), Projectile.java via GamePanel (receives the `tryDestroyAt` call when a bullet hits a tile)

---

### HUD.java
**Where it lives:** src/ui/

**What it does:** Draws the info bar at the bottom of the screen in local play, showing both teams' current lives and scores. It also draws the game-over overlay — the big yellow winner announcement and the "press ENTER to restart" prompt — when the match ends.

**Talks to:** GamePanel.java (reads `player.lives`, `player.score`, `player2.lives`, `player2.score`, `winMessage`, and screen dimensions to know what to print and where)

---

### MenuScreen.java
**Where it lives:** src/ui/

**What it does:** Manages all the screens the player sees before the game starts: the main menu, the name-entry box, the IP-entry box, the team-selection screen, and the network waiting room. It uses an internal sub-state machine to track which screen is active and routes key presses to the right handler. When the player finishes entering their details, it calls GamePanel to kick off the host or join flow.

**Talks to:** GamePanel.java (calls `startLocalGame`, `startHostGame`, `startJoinGame` when the player confirms), NetworkUtils.java (calls `getLanIp` to show the host's IP in the waiting room), GameClient.java (reads lobby connection count to show how many players are waiting)

---

### PauseMenu.java
**Where it lives:** src/ui/

**What it does:** Draws the pause overlay — a semi-transparent black screen with "PAUSED", two menu options (RESUME and EXIT TO MAIN MENU), and a key-hint line at the bottom. It tracks which option is highlighted and lets GamePanel move the cursor up and down.

**Talks to:** GamePanel.java (calls `draw`, `moveUp`, `moveDown`; reads `selectedOption` to decide what happens when the player presses Enter)

---

### MessageType.java
**Where it lives:** src/network/

**What it does:** A simple list of all the message types that travel between server and clients: JOIN, JOIN_ACK, INPUT, LOBBY_STATUS, GAME_STATE, GAME_OVER, CHAT, PLAYER_LEFT, REMATCH, and REMATCH_READY. Every message sent over the network is tagged with one of these so the receiver knows how to handle it.

**Talks to:** Message.java (every Message carries a MessageType), Protocol.java (creates messages of each type), GameServer.java and GameClient.java (switch on the type to handle incoming messages)

---

### Message.java
**Where it lives:** src/network/

**What it does:** Pairs a message type with a JSON payload string and converts it to and from the wire format: `{"type":"…","payload":{…}}`. It has a `toJson()` method for sending and a static `fromJson()` method for parsing the incoming text back into a usable object.

**Talks to:** Protocol.java (creates Message objects with built payloads), GameServer.java and GameClient.java (call `toJson` to send, `fromJson` to parse), ClientHandler.java (parses each incoming line from a player's socket)

---

### Protocol.java
**Where it lives:** src/network/

**What it does:** A collection of helper methods that build correctly formatted Message objects for every action in the game — joining, sending key input, broadcasting game state, ending the game, notifying about a disconnect, sending chat, and handling rematches. Instead of assembling JSON strings by hand in multiple places, the code calls a single `Protocol.buildXxx()` method and gets a ready-to-send Message back.

**Talks to:** Message.java (wraps each JSON payload into a Message), GameServer.java (calls build methods when broadcasting to clients), GameClient.java (calls `buildJoin`, `buildInput`, `buildChat` when sending to the server)

---

### GameServer.java
**Where it lives:** src/network/

**What it does:** The authoritative game server. It opens a TCP port, waits for players to connect and pick a team (up to 4 total, 2 per team), then starts the game when the host player signals ready or automatically after 30 seconds if at least 2 players are present. Once started, it runs a 60-update-per-second loop: it advances the game state, then broadcasts the full state to every client as JSON. The server is the single source of truth — clients cannot cheat by modifying what they send.

**Talks to:** ClientHandler.java (spawns one per connected player; receives input messages from them), ServerGameState.java (calls `update` every tick and serializes with `toJson`), Protocol.java (builds all outgoing messages), NetworkUtils.java (gets the LAN IP to include in lobby status broadcasts)

---

### ClientHandler.java
**Where it lives:** src/network/

**What it does:** Runs on its own thread, one per connected player, on the server side. It continuously reads incoming JSON lines from that player's socket and forwards them to GameServer to act on (input updates, chat, rematch votes). When the socket closes — whether the player quit or the network dropped — it notifies the server so it can remove that player and keep everyone else informed.

**Talks to:** GameServer.java (passes each incoming message up and calls `clientDisconnected` on close), Message.java (parses each line from the socket)

---

### ServerGameState.java
**Where it lives:** src/network/

**What it does:** The complete game simulation running entirely on the server. It loads the map, tracks every player's position, direction, lives, and queued input, moves bullets, destroys brick tiles, handles tank respawning with random spawn points, and checks win conditions every tick. When GameServer asks, it serializes everything into a JSON string so clients can render it. It has no display code at all — it is safe to run without a screen.

**Talks to:** GameServer.java (calls `update`, `applyInput`, `markDisconnected`, `reset`, and `toJson`; this is the only class that talks to it directly)

---

### GameClient.java
**Where it lives:** src/network/

**What it does:** Opens a TCP connection to the server, sends the JOIN message, waits for the server to accept the player, then starts a background thread that listens for everything the server sends. It stores the latest game-state JSON so GamePanel can read and render it, and it tracks lobby info, game-over status, incoming chat messages, and the rematch signal. GamePanel calls `sendInput` every frame with the currently held keys.

**Talks to:** GamePanel.java (GamePanel calls `sendInput` each frame and reads `latestState`, `isGameOver`, etc.; GameClient posts chat messages directly to `chatOverlay`), Protocol.java (builds outgoing JOIN, INPUT, and CHAT messages), Message.java (parses every incoming server message)

---

### NetworkUtils.java
**Where it lives:** src/network/

**What it does:** Has one job: find and return the machine's local network IP address (like 192.168.1.5) so that the host player can see it and tell others where to connect. It skips loopback and virtual interfaces and falls back to 127.0.0.1 if no suitable address is found.

**Talks to:** GameServer.java (calls `getLanIp` when broadcasting lobby status), MenuScreen.java (calls `getLanIp` to display in the waiting room)

---

### ChatOverlay.java
**Where it lives:** src/chat/

**What it does:** Keeps a sliding window of up to 6 recent chat messages and draws them at the bottom-left of the screen during network play. Messages fade out gradually over 8 seconds. The class is thread-safe because the background network listener and the main game thread may both access the message list at the same time.

**Talks to:** GamePanel.java (calls `draw` every frame; the chat-input key handler calls `addMessage` when a message is sent), GameClient.java (calls `addMessage` whenever a CHAT message arrives from the server)

---

### Main.java
**Where it lives:** src/

**What it does:** The single entry point for the whole program. It creates the JFrame window, adds GamePanel to it, makes the window visible, then calls `panel.start()` to launch the game loop thread. Everything else flows from there.

**Talks to:** GamePanel.java (creates it, adds it to the frame, and calls `start`)

---

## Other important files

**run.sh** — Compiles all Java source files found under `src/` (recursively), copies the `res/` assets into `out/`, then runs `Main`. To use it: open a terminal in the project folder and run `./run.sh`. Make it executable first with `chmod +x run.sh` if needed.

**CLAUDE.md** — Instructions and context for the AI assistant used during development. It describes the project's goals, tech stack, coding style, and completed milestone checklists. It has no effect on the game itself.

**res/ folder** — Contains all the image and map assets the game needs at runtime. Tank sprites (in four directions, two color variants) are in `res/player/`. Tile images (brick, black, and eagle) are in `res/tiles/`. The map layout is a plain text file at `res/maps/map1.txt` where each number corresponds to a tile type.

**out/ folder** — The build output directory. `run.sh` compiles `.class` files here and copies `res/` into it. Do not manually edit anything in this folder — every build wipes and recreates it.

---

## How a game session works

When you run `./run.sh`, the window opens to the main menu. From there, pressing L (or Enter/Space) starts a local two-player game immediately — both players control tanks on the same machine with WASD and arrow keys respectively. The game loop ticks 60 times per second, moving tanks and bullets, checking collisions, and redrawing the screen. When a bullet hits a brick wall, the wall disappears. When a bullet hits an enemy tank, that tank loses a life and respawns after a short delay. The game ends when one team's Eagle is destroyed or all of their tanks are permanently eliminated.

For a network game, the hosting player presses H, enters their name, picks a team, and waits. Their machine starts a GameServer in the background and connects as the first player. Other players on the same network press J, type the host's IP address, enter their name, and pick a team. Once at least two players are connected, the host presses Enter to start. From that point on, the server runs the entire simulation — it updates positions, processes bullets, and checks win conditions. Each client simply sends the keys the local player is holding and renders the state snapshot the server broadcasts back. When someone wins, the game-over screen appears and all players can press R together to rematch or M to return to the main menu.

---

## Networking explained simply

Think of the server as a referee and the clients as players calling in their moves by radio. Every frame, each client tells the referee "I'm pressing Up and Shoot right now." The referee collects all the moves, advances the game by one step, and immediately reads out the full scoreboard to everyone: every tank's position, every bullet, every life count, the current map. The clients trust this read-out completely and draw exactly what the referee describes — they never decide for themselves whether a bullet hit or a player died.

This design is called an "authoritative server." It means no single player can cheat by claiming they didn't get hit or that their tank moved somewhere it couldn't reach, because the server — not the client — is the one doing the math. The downside is that if your connection to the server is slow, your inputs arrive late and your tank feels sluggish. For a local-network game like this one, that delay is tiny and practically unnoticeable.
