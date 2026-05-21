package network;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Authoritative server-side game state. No Swing/AWT — safe to run headless.
 * One instance is created by GameServer when the game starts.
 */
public class ServerGameState {

    // ── Constants ─────────────────────────────────────────────────────────────

    static final int TILE_SIZE     = 48;
    static final int COLS          = 16;
    static final int ROWS          = 12;
    static final int SCREEN_W      = TILE_SIZE * COLS;
    static final int SCREEN_H      = TILE_SIZE * ROWS;
    static final int PLAYER_SPEED  = 4;
    static final int BULLET_SPEED  = 10;
    static final int BULLET_W      = 10;
    static final int BULLET_H      = 10;
    static final int SHOT_COOLDOWN = 30;
    static final int RESPAWN_TICKS = 120;

    // Indexed by tile index 0-5 (EMPTY,BRICK,EAGLE,STEEL,WATER,GRASS)
    // Eagle (2) is intentionally false — tanks pass through; bullets check it separately
    static final boolean[] TILE_COLLISION    = {false, true,  false, true,  true,  false};
    static final boolean[] TILE_DESTRUCTIBLE = {false, true,  false, false, false, false};

    // Per-team spawn data (0 = first player on that team, 1 = second)
    static final int[] RED_SPAWN_X  = {  4*TILE_SIZE, 11*TILE_SIZE };
    static final int[] RED_SPAWN_Y  = { 10*TILE_SIZE, 10*TILE_SIZE };
    static final int[] BLUE_SPAWN_X = {  4*TILE_SIZE, 11*TILE_SIZE };
    static final int[] BLUE_SPAWN_Y = {    TILE_SIZE,    TILE_SIZE  };

    // Eagle indices: 0 = RED eagle (bottom), 1 = BLUE eagle (top)
    static final String[] EAGLE_TEAM = { "RED",  "BLUE" };
    static final int[]    EAGLE_COL  = { 7,       7     };
    static final int[]    EAGLE_ROW  = { 11,      0     };

    // ── Inner data classes ────────────────────────────────────────────────────

    static class PlayerState {
        int    playerId;
        String team;
        String playerName = "Player";
        int    x, y;
        // Saved default spawn so respawn fallback is team-consistent
        int    spawnX, spawnY;
        String spawnDir;
        String direction;
        int    lives        = 3;
        boolean active      = true;
        boolean connected   = true; // false = network-disconnected; permanently inactive
        int    shotCounter  = SHOT_COOLDOWN;
        int    respawnTimer = 0;

        // Latest input for this player (written by client threads, read by game loop)
        volatile boolean wantsUp, wantsDown, wantsLeft, wantsRight, wantsShoot;

        void hit() {
            active = false;
            lives--;
            respawnTimer = (lives > 0) ? RESPAWN_TICKS : 0;
        }
    }

    static class BulletState {
        int    x, y;
        String direction;
        int    ownerId;
        boolean alive = true;
    }

    // ── State fields ──────────────────────────────────────────────────────────

    // Column-major: mapTileNum[col][row]
    final int[][] mapTileNum = new int[COLS][ROWS];

    PlayerState[]      players;
    final boolean[]    eagleAlive = { true, true };
    final List<BulletState> bullets = new ArrayList<>();

    int     tick       = 0;
    boolean gameOver   = false;
    String  winnerTeam = null;

    private final Random rand = new Random();

    // ── Construction ──────────────────────────────────────────────────────────

    public ServerGameState(int numPlayers, String[] teams, String[] names) {
        loadMap();
        players = new PlayerState[numPlayers];
        int redIdx = 0, blueIdx = 0;
        for (int i = 0; i < numPlayers; i++) {
            PlayerState p = new PlayerState();
            p.playerId   = i;
            p.team       = teams[i];
            p.playerName = (names != null && i < names.length && names[i] != null && !names[i].isEmpty())
                           ? names[i] : "Player";
            if ("RED".equals(p.team)) {
                int idx    = Math.min(redIdx, RED_SPAWN_X.length - 1);
                p.spawnX   = RED_SPAWN_X[idx];
                p.spawnY   = RED_SPAWN_Y[idx];
                p.spawnDir = "up";
                redIdx++;
            } else {
                int idx    = Math.min(blueIdx, BLUE_SPAWN_X.length - 1);
                p.spawnX   = BLUE_SPAWN_X[idx];
                p.spawnY   = BLUE_SPAWN_Y[idx];
                p.spawnDir = "down";
                blueIdx++;
            }
            p.x         = p.spawnX;
            p.y         = p.spawnY;
            p.direction = p.spawnDir;
            players[i]  = p;
        }
    }

    private void loadMap() {
        try {
            InputStream is = ServerGameState.class.getResourceAsStream("/maps/map1.txt");
            if (is == null) {
                System.err.println("[ServerGameState] map1.txt not found on classpath");
                return;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            for (int row = 0; row < ROWS; row++) {
                String line = br.readLine();
                if (line == null) break;
                String[] parts = line.trim().split("\\s+");
                for (int col = 0; col < COLS && col < parts.length; col++) {
                    mapTileNum[col][row] = Integer.parseInt(parts[col]);
                }
            }
            br.close();
        } catch (Exception e) {
            System.err.println("[ServerGameState] loadMap error: " + e.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Store the latest input for a player.
     * Called from client-handler threads; uses volatile writes so the game loop
     * sees updates without a hard lock on each input packet.
     */
    public void applyInput(int playerId, String keysHeld) {
        if (playerId < 0 || playerId >= players.length) return;
        PlayerState p = players[playerId];
        if (!p.connected) return;
        String k = keysHeld != null ? keysHeld.toUpperCase() : "";
        p.wantsUp    = k.contains("UP");
        p.wantsDown  = k.contains("DOWN");
        p.wantsLeft  = k.contains("LEFT");
        p.wantsRight = k.contains("RIGHT");
        p.wantsShoot = k.contains("SHOOT");
    }

    /** Deactivates a player who has disconnected. Lives are preserved so win conditions don't fire. */
    public synchronized void markDisconnected(int playerId) {
        if (playerId < 0 || playerId >= players.length) return;
        PlayerState p = players[playerId];
        p.connected = false;
        p.active    = false;
        // lives intentionally preserved: avoids triggering false win-condition on disconnect
    }

    /**
     * Resets all game state for a rematch. Preserves playerName, team, and connected status.
     * Connected players are fully re-initialized; disconnected players remain inactive.
     */
    public synchronized void reset() {
        loadMap();
        eagleAlive[0] = true;
        eagleAlive[1] = true;
        bullets.clear();
        tick       = 0;
        gameOver   = false;
        winnerTeam = null;

        int redIdx = 0, blueIdx = 0;
        for (PlayerState p : players) {
            if ("RED".equals(p.team)) {
                int idx  = Math.min(redIdx, RED_SPAWN_X.length - 1);
                p.spawnX   = RED_SPAWN_X[idx];
                p.spawnY   = RED_SPAWN_Y[idx];
                p.spawnDir = "up";
                redIdx++;
            } else {
                int idx  = Math.min(blueIdx, BLUE_SPAWN_X.length - 1);
                p.spawnX   = BLUE_SPAWN_X[idx];
                p.spawnY   = BLUE_SPAWN_Y[idx];
                p.spawnDir = "down";
                blueIdx++;
            }
            p.x            = p.spawnX;
            p.y            = p.spawnY;
            p.direction    = p.spawnDir;
            p.lives        = 3;
            p.active       = p.connected; // disconnected players stay inactive
            p.shotCounter  = SHOT_COOLDOWN;
            p.respawnTimer = 0;
            p.wantsUp = p.wantsDown = p.wantsLeft = p.wantsRight = p.wantsShoot = false;
        }
    }

    /** Advance the game by one tick. Called exclusively by the server game-loop thread. */
    public synchronized void update() {
        if (gameOver) return;
        tick++;
        for (PlayerState p : players) updatePlayer(p);
        updateBullets();
        checkWinConditions();
    }

    /** Serialize the full game state to a JSON string for GAME_STATE broadcast. */
    public synchronized String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"tick\":").append(tick);
        sb.append(",\"gameOver\":").append(gameOver);
        sb.append(",\"winnerTeam\":\"").append(winnerTeam != null ? winnerTeam : "").append('"');

        // Eagles
        sb.append(",\"eagles\":[");
        for (int i = 0; i < 2; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"team\":\"").append(EAGLE_TEAM[i])
              .append("\",\"alive\":").append(eagleAlive[i]).append('}');
        }
        sb.append(']');

        // Players
        sb.append(",\"players\":[");
        for (int i = 0; i < players.length; i++) {
            if (i > 0) sb.append(',');
            PlayerState p = players[i];
            sb.append("{\"id\":").append(p.playerId)
              .append(",\"playerName\":\"").append(escape(p.playerName)).append('"')
              .append(",\"team\":\"").append(p.team).append('"')
              .append(",\"x\":").append(p.x)
              .append(",\"y\":").append(p.y)
              .append(",\"dir\":\"").append(p.direction).append('"')
              .append(",\"lives\":").append(p.lives)
              .append(",\"active\":").append(p.active)
              .append('}');
        }
        sb.append(']');

        // Bullets
        sb.append(",\"bullets\":[");
        boolean first = true;
        for (BulletState b : bullets) {
            if (!b.alive) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"x\":").append(b.x)
              .append(",\"y\":").append(b.y)
              .append(",\"dir\":\"").append(b.direction).append('"')
              .append(",\"ownerId\":").append(b.ownerId)
              .append('}');
        }
        sb.append(']');

        // Map — serialized as rows × columns (row-major) for easy client parsing
        sb.append(",\"map\":[");
        for (int row = 0; row < ROWS; row++) {
            if (row > 0) sb.append(',');
            sb.append('[');
            for (int col = 0; col < COLS; col++) {
                if (col > 0) sb.append(',');
                sb.append(mapTileNum[col][row]);
            }
            sb.append(']');
        }
        sb.append("]}");
        return sb.toString();
    }

    // ── Player update ─────────────────────────────────────────────────────────

    private void updatePlayer(PlayerState p) {
        if (!p.connected) return;
        if (!p.active) {
            if (p.respawnTimer > 0) {
                p.respawnTimer--;
            } else if (p.lives > 0) {
                int[] spawn = findSafeSpawnPoint(p.team);
                p.x         = (spawn != null) ? spawn[0] : p.spawnX;
                p.y         = (spawn != null) ? spawn[1] : p.spawnY;
                p.direction = p.spawnDir;
                p.active     = true;
                p.shotCounter = SHOT_COOLDOWN;
            }
            return;
        }

        // Movement
        if (p.wantsUp || p.wantsDown || p.wantsLeft || p.wantsRight) {
            String dir;
            if      (p.wantsUp)    dir = "up";
            else if (p.wantsDown)  dir = "down";
            else if (p.wantsLeft)  dir = "left";
            else                   dir = "right";
            p.direction = dir;

            int nx = p.x, ny = p.y;
            switch (dir) {
                case "up":    ny -= PLAYER_SPEED; break;
                case "down":  ny += PLAYER_SPEED; break;
                case "left":  nx -= PLAYER_SPEED; break;
                default:      nx += PLAYER_SPEED; break;
            }

            if (!tileBlocks(nx, ny, TILE_SIZE, TILE_SIZE, dir) &&
                !playerBlocks(p, nx, ny)) {
                p.x = nx;
                p.y = ny;
            }
        }

        // Shooting
        if (p.wantsShoot && p.shotCounter >= SHOT_COOLDOWN) {
            spawnBullet(p);
            p.shotCounter = 0;
        }
        if (p.shotCounter < SHOT_COOLDOWN) p.shotCounter++;
    }

    private void spawnBullet(PlayerState p) {
        BulletState b = new BulletState();
        b.ownerId   = p.playerId;
        b.direction = p.direction;
        int half = TILE_SIZE / 2 - 5; // center minus half bullet width (matches M1)
        switch (p.direction) {
            case "up":    b.x = p.x + half; b.y = p.y;             break;
            case "down":  b.x = p.x + half; b.y = p.y + TILE_SIZE; break;
            case "left":  b.x = p.x;        b.y = p.y + half;      break;
            default:      b.x = p.x + TILE_SIZE; b.y = p.y + half; break;
        }
        bullets.add(b);
    }

    // ── Bullet update ─────────────────────────────────────────────────────────

    private void updateBullets() {
        Iterator<BulletState> it = bullets.iterator();
        while (it.hasNext()) {
            BulletState b = it.next();

            if (!b.alive) { it.remove(); continue; }

            switch (b.direction) {
                case "up":    b.y -= BULLET_SPEED; break;
                case "down":  b.y += BULLET_SPEED; break;
                case "left":  b.x -= BULLET_SPEED; break;
                default:      b.x += BULLET_SPEED; break;
            }

            // Boundary check
            if (b.x < 0 || b.x > SCREEN_W || b.y < 0 || b.y > SCREEN_H) {
                it.remove();
                continue;
            }

            // Tank collision — any player except the shooter
            boolean hit = false;
            for (PlayerState t : players) {
                if (t.playerId == b.ownerId || !t.active) continue;
                if (rectsOverlap(b.x, b.y, BULLET_W, BULLET_H,
                                 t.x,  t.y, TILE_SIZE, TILE_SIZE)) {
                    t.hit();
                    it.remove();
                    hit = true;
                    break;
                }
            }
            if (hit) continue;

            // Tile collision — compute the bullet's leading-edge world coordinate
            int leadX, leadY;
            switch (b.direction) {
                case "up":   leadX = b.x + BULLET_W / 2; leadY = b.y;               break;
                case "down": leadX = b.x + BULLET_W / 2; leadY = b.y + BULLET_H;    break;
                case "left": leadX = b.x;                 leadY = b.y + BULLET_H / 2; break;
                default:     leadX = b.x + BULLET_W;      leadY = b.y + BULLET_H / 2; break;
            }

            int col = leadX / TILE_SIZE;
            int row = leadY / TILE_SIZE;
            if (col >= 0 && col < COLS && row >= 0 && row < ROWS) {
                int idx = mapTileNum[col][row];
                if (idx == 2) {
                    // Eagle tile — stop bullet and destroy the eagle
                    tryDestroyEagleAt(col, row);
                    it.remove();
                } else if (idx >= 0 && idx < TILE_COLLISION.length && TILE_COLLISION[idx]) {
                    if (idx < TILE_DESTRUCTIBLE.length && TILE_DESTRUCTIBLE[idx]) {
                        mapTileNum[col][row] = 0; // destroy BRICK
                    }
                    it.remove();
                }
            }
        }
    }

    private void tryDestroyEagleAt(int col, int row) {
        for (int i = 0; i < 2; i++) {
            if (!eagleAlive[i]) continue; // already destroyed — skip
            if (EAGLE_COL[i] == col && EAGLE_ROW[i] == row) {
                eagleAlive[i] = false;
                mapTileNum[col][row] = 0;
            }
        }
    }

    // ── Respawn ───────────────────────────────────────────────────────────────

    /**
     * Picks a random EMPTY tile on the team's half of the map that is not within
     * 3 tiles of any active player or any eagle. Returns {x, y} in pixels, or
     * null if no safe tile was found after 20 attempts (caller uses default spawn).
     */
    private int[] findSafeSpawnPoint(String team) {
        boolean bottomHalf = "RED".equals(team);
        int rowStart = bottomHalf ? ROWS / 2 : 0;
        int rowEnd   = bottomHalf ? ROWS     : ROWS / 2;

        for (int attempt = 0; attempt < 20; attempt++) {
            int col = rand.nextInt(COLS);
            int row = rowStart + rand.nextInt(rowEnd - rowStart);

            if (mapTileNum[col][row] != 0) continue; // must be EMPTY

            boolean tooClose = false;

            for (PlayerState p : players) {
                if (!p.active) continue;
                int pc = p.x / TILE_SIZE;
                int pr = p.y / TILE_SIZE;
                if (Math.abs(pc - col) <= 3 && Math.abs(pr - row) <= 3) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;

            for (int i = 0; i < 2; i++) {
                if (Math.abs(EAGLE_COL[i] - col) <= 3 && Math.abs(EAGLE_ROW[i] - row) <= 3) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;

            return new int[]{ col * TILE_SIZE, row * TILE_SIZE };
        }
        return null; // caller falls back to default spawn
    }

    // ── Win conditions ────────────────────────────────────────────────────────

    private void checkWinConditions() {
        // Eagle destroyed
        if (!eagleAlive[0]) { winnerTeam = "BLUE"; gameOver = true; return; }
        if (!eagleAlive[1]) { winnerTeam = "RED";  gameOver = true; return; }

        // All players on a team eliminated (lives==0 and not active)
        boolean redAlive  = false;
        boolean blueAlive = false;
        for (PlayerState p : players) {
            if (p.active || p.lives > 0) {
                if ("RED".equals(p.team))  redAlive  = true;
                else                        blueAlive = true;
            }
        }
        if (!redAlive  && hasTeam("RED"))  { winnerTeam = "BLUE"; gameOver = true; }
        else if (!blueAlive && hasTeam("BLUE")) { winnerTeam = "RED";  gameOver = true; }
    }

    private boolean hasTeam(String team) {
        for (PlayerState p : players) if (team.equals(p.team)) return true;
        return false;
    }

    // ── Collision helpers ─────────────────────────────────────────────────────

    /**
     * Returns true if an entity of size (w×h) placed at (nx, ny) moving in
     * `dir` would overlap a collidable tile.  Mirrors CollisionChecker logic.
     */
    private boolean tileBlocks(int nx, int ny, int w, int h, String dir) {
        int col1, col2, row1, row2;
        switch (dir) {
            case "up":
                if (ny < 0) return true;
                row1 = ny / TILE_SIZE;
                col1 = nx / TILE_SIZE;
                col2 = (nx + w - 1) / TILE_SIZE;
                return isTileCollision(col1, row1) || isTileCollision(col2, row1);
            case "down":
                row1 = (ny + h - 1) / TILE_SIZE;
                if (row1 >= ROWS) return true;
                col1 = nx / TILE_SIZE;
                col2 = (nx + w - 1) / TILE_SIZE;
                return isTileCollision(col1, row1) || isTileCollision(col2, row1);
            case "left":
                if (nx < 0) return true;
                col1 = nx / TILE_SIZE;
                row1 = ny / TILE_SIZE;
                row2 = (ny + h - 1) / TILE_SIZE;
                return isTileCollision(col1, row1) || isTileCollision(col1, row2);
            default: // right
                col1 = (nx + w - 1) / TILE_SIZE;
                if (col1 >= COLS) return true;
                row1 = ny / TILE_SIZE;
                row2 = (ny + h - 1) / TILE_SIZE;
                return isTileCollision(col1, row1) || isTileCollision(col1, row2);
        }
    }

    private boolean isTileCollision(int col, int row) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return true;
        int idx = mapTileNum[col][row];
        return idx >= 0 && idx < TILE_COLLISION.length && TILE_COLLISION[idx];
    }

    /** Returns true if the mover's projected rect at (nx, ny) overlaps any other active tank. */
    private boolean playerBlocks(PlayerState mover, int nx, int ny) {
        for (PlayerState other : players) {
            if (other == mover || !other.active) continue;
            if (rectsOverlap(nx, ny, TILE_SIZE, TILE_SIZE,
                             other.x, other.y, TILE_SIZE, TILE_SIZE)) return true;
        }
        return false;
    }

    private static boolean rectsOverlap(int x1, int y1, int w1, int h1,
                                        int x2, int y2, int w2, int h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
