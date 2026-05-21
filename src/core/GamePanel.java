package core;

import chat.ChatOverlay;
import entity.Eagle;
import entity.Entity;
import entity.Player;
import entity.Projectile;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import map.TileManager;
import network.GameClient;
import network.GameServer;
import ui.HUD;
import ui.MenuScreen;
import ui.PauseMenu;

public class GamePanel extends JPanel implements Runnable {

    public final int tileSize      = Constants.TILE_SIZE;
    public final int maxScreenCol  = Constants.MAX_SCREEN_COL;
    public final int maxScreenRow  = Constants.MAX_SCREEN_ROW;
    public final int screenWidth   = Constants.WINDOW_WIDTH;
    public final int screenHeight  = Constants.WINDOW_HEIGHT;

    public TileManager tileM   = new TileManager(this);
    public KeyHandler keyH     = new KeyHandler();
    public CollisionChecker cChecker = new CollisionChecker(this);
    public Player player       = new Player(this, keyH, 1);
    public Player player2      = new Player(this, keyH, 2);
    public List<Entity> tanks  = new ArrayList<>();
    public List<Eagle>  eagles     = new ArrayList<>();
    public String       winMessage = null;
    public ArrayList<Projectile> projectileList = new ArrayList<>();
    public GameState state = GameState.MENU;

    // ── Network mode (Module 2C/2D) ───────────────────────────────────────────
    public boolean    networkMode = false;
    public boolean    isHost      = false;
    public GameClient client      = null;

    // ── Tank sprites (network renderer) ──────────────────────────────────────
    // Index order: 0=up, 1=down, 2=left, 3=right
    private final BufferedImage[] redTankSprites  = new BufferedImage[4];
    private final BufferedImage[] blueTankSprites = new BufferedImage[4];

    // ── Chat ──────────────────────────────────────────────────────────────────
    public final ChatOverlay chatOverlay = new ChatOverlay();

    private boolean       chatInputActive = false;
    private boolean       chatInputTeam   = false;
    private final StringBuilder chatInputBuffer = new StringBuilder();

    private HUD        hud        = new HUD(this);
    private MenuScreen menuScreen = new MenuScreen(this);
    private PauseMenu  pauseMenu  = new PauseMenu();

    // Cached server-state JSON shown while paused (server keeps running; display is frozen)
    private String pausedNetworkJson = null;

    // Network game-over / rematch state
    private boolean waitingForRematch    = false;
    private String  playerLeftNotif      = null;
    private long    playerLeftNotifUntil = 0;

    private Thread gameThread;
    private volatile boolean running;
    private int fps;

    public GamePanel() {
        setPreferredSize(new Dimension(screenWidth, screenHeight));
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
        addKeyListener(keyH);
        setFocusable(true);
        tanks.add(player);
        tanks.add(player2);
        // Eagle positions mirror the map: col 7, row 11 (team 1 bottom) and col 7, row 0 (team 2 top)
        eagles.add(new Eagle(this, 1, 7, 11));
        eagles.add(new Eagle(this, 2, 7,  0));

        loadTankSprites();

        // Menu key handler — delegates entirely to MenuScreen sub-state machine
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (state != GameState.MENU || networkMode) return;
                menuScreen.handleKey(e);
            }
        });

        // Pause / pause-menu key handler (PLAYING and PAUSED states only)
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_ESCAPE) {
                    if (state == GameState.PLAYING) {
                        state = GameState.PAUSED;
                        pauseMenu.selectedOption = 0;
                        if (networkMode && client != null) {
                            client.sendInput(""); // stop server-side movement while paused
                            pausedNetworkJson = client.getLatestState();
                        }
                    } else if (state == GameState.PAUSED) {
                        state = GameState.PLAYING;
                        pausedNetworkJson = null;
                    }
                } else if (state == GameState.PAUSED) {
                    if (code == KeyEvent.VK_UP) {
                        pauseMenu.moveUp();
                    } else if (code == KeyEvent.VK_DOWN) {
                        pauseMenu.moveDown();
                    } else if (code == KeyEvent.VK_ENTER) {
                        if (pauseMenu.selectedOption == 0) {
                            state = GameState.PLAYING;
                            pausedNetworkJson = null;
                        } else {
                            exitToMainMenu();
                        }
                    }
                }
            }
        });

        // Chat key handler (network play only)
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (state != GameState.PLAYING || !networkMode) return;
                if (!chatInputActive) {
                    if (e.getKeyCode() == KeyEvent.VK_T) {
                        chatInputActive = true;
                        chatInputTeam   = true;
                        chatInputBuffer.setLength(0);
                    } else if (e.getKeyCode() == KeyEvent.VK_Y) {
                        chatInputActive = true;
                        chatInputTeam   = false;
                        chatInputBuffer.setLength(0);
                    }
                } else {
                    int code = e.getKeyCode();
                    if (code == KeyEvent.VK_ENTER) {
                        String text = chatInputBuffer.toString().trim();
                        if (!text.isEmpty() && client != null) {
                            client.sendChat(text, chatInputTeam ? "TEAM" : "ALL");
                        }
                        chatInputActive = false;
                        chatInputBuffer.setLength(0);
                    } else if (code == KeyEvent.VK_ESCAPE) {
                        chatInputActive = false;
                        chatInputBuffer.setLength(0);
                    } else if (code == KeyEvent.VK_BACK_SPACE) {
                        if (chatInputBuffer.length() > 0)
                            chatInputBuffer.deleteCharAt(chatInputBuffer.length() - 1);
                    } else {
                        char c = e.getKeyChar();
                        if (c != KeyEvent.CHAR_UNDEFINED && c >= 32 && c < 127)
                            chatInputBuffer.append(c);
                    }
                }
            }
        });
    }

    // ── Sprite loading ────────────────────────────────────────────────────────

    private void loadTankSprites() {
        String[] dirs = {"up", "down", "left", "right"};
        for (int i = 0; i < 4; i++) {
            // RED team sprites use *2.png (matches local Player playerNum==1)
            redTankSprites[i]  = loadSprite("/player/" + dirs[i] + "2.png");
            // BLUE team sprites use *.png (matches local Player playerNum==2)
            blueTankSprites[i] = loadSprite("/player/" + dirs[i] + ".png");
        }
    }

    private BufferedImage loadSprite(String path) {
        try {
            var is = getClass().getResourceAsStream(path);
            if (is == null) return null;
            return ImageIO.read(is);
        } catch (IOException e) {
            System.err.println("[GamePanel] Cannot load sprite: " + path);
            return null;
        }
    }

    // ── Network mode methods (Module 2C) ──────────────────────────────────────

    /** Called every tick when networkMode=true. Sends input; detects game-over and rematch. */
    private void updateNetwork() {
        if (client == null || !client.isConnected()) {
            networkMode       = false;
            isHost            = false;
            client            = null;
            waitingForRematch = false;
            state             = GameState.MENU;
            menuScreen.resetToMain();
            return;
        }

        // Rematch confirmed by server — restart the round
        if (client.isRematchReady()) {
            client.clearRematchReady();
            waitingForRematch = false;
            winMessage        = null;
            pausedNetworkJson = null;
            chatOverlay.clear();
            state = GameState.PLAYING;
            return;
        }

        // Consume any player-left notification so we can display it for 3 seconds
        String leftMsg = client.getPlayerLeftMessage();
        if (leftMsg != null) {
            playerLeftNotif      = leftMsg;
            playerLeftNotifUntil = System.currentTimeMillis() + 3000;
            client.clearPlayerLeftMessage();
        }

        if (state == GameState.GAME_OVER) {
            if (!waitingForRematch && keyH.rPressed) {
                keyH.rPressed     = false;
                waitingForRematch = true;
                client.sendInput("REMATCH");
            }
            if (keyH.mPressed) {
                keyH.mPressed = false;
                exitToMainMenu();
            }
            return;
        }

        if (client.isGameOver()) {
            winMessage = client.getWinner() + " TEAM WINS!";
            state      = GameState.GAME_OVER;
            return;
        }

        client.sendInput(buildKeysHeld());
    }

    /** Combines both key sets (WASD and arrows) into a comma-separated keysHeld string. */
    private String buildKeysHeld() {
        StringBuilder sb = new StringBuilder();
        if (keyH.upPressed    || keyH.upArrowPressed)    appendKey(sb, "UP");
        if (keyH.downPressed  || keyH.downArrowPressed)  appendKey(sb, "DOWN");
        if (keyH.leftPressed  || keyH.leftArrowPressed)  appendKey(sb, "LEFT");
        if (keyH.rightPressed || keyH.rightArrowPressed) appendKey(sb, "RIGHT");
        if (keyH.enterPressed || keyH.spacePressed)      appendKey(sb, "SHOOT");
        return sb.toString();
    }

    private static void appendKey(StringBuilder sb, String key) {
        if (sb.length() > 0) sb.append(',');
        sb.append(key);
    }

    /** Starts a local (M1) game. */
    public void startLocalGame() {
        networkMode       = false;
        keyH.enterPressed = false;
        keyH.spacePressed = false;
        state             = GameState.PLAYING;
    }

    /** Starts a GameServer in a background thread, then connects as the host player. */
    public void startHostGame(String name, String team) {
        new Thread(() -> {
            new Thread(() -> {
                try { new GameServer().start(5000); }
                catch (Exception e) { System.err.println("[GamePanel] Server error: " + e.getMessage()); }
            }, "game-server").start();

            try { Thread.sleep(400); } catch (InterruptedException ignored) {}

            try {
                GameClient c = new GameClient();
                c.connect("localhost", 5000, name, team);
                c.setGamePanel(GamePanel.this);
                client      = c;
                networkMode = true;
                isHost      = true;
            } catch (Exception e) {
                System.err.println("[GamePanel] Host connect failed: " + e.getMessage());
                String msg = e.getMessage();
                javax.swing.SwingUtilities.invokeLater(() ->
                        menuScreen.showTeamFullError(msg != null ? msg : "Connection failed"));
            }
        }, "host-setup").start();
    }

    /** Connects to the given IP as a joining player with the chosen name and team. */
    public void startJoinGame(String ip, String name, String team) {
        new Thread(() -> {
            try {
                GameClient c = new GameClient();
                c.connect(ip, 5000, name, team);
                c.setGamePanel(GamePanel.this);
                client      = c;
                networkMode = true;
                isHost      = false;
            } catch (Exception e) {
                System.err.println("[GamePanel] Join failed: " + e.getMessage());
                String msg = e.getMessage();
                javax.swing.SwingUtilities.invokeLater(() ->
                        menuScreen.showTeamFullError(msg != null ? msg : "Connection failed"));
            }
        }, "join-setup").start();
    }

    // ── Network rendering ─────────────────────────────────────────────────────

    /** Top-level network frame renderer. */
    private void paintNetworkFrame(Graphics2D g2) {
        try {
            paintNetworkFrameImpl(g2);
        } catch (Exception e) {
            System.err.println("[GamePanel] paintNetworkFrame error: " + e.getMessage());
        }
    }

    private void paintNetworkFrameImpl(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Use the cached (frozen) state while paused so the display doesn't update
        String json = (state == GameState.PAUSED && pausedNetworkJson != null)
                      ? pausedNetworkJson : client.getLatestState();
        if (json == null) {
            // Waiting for first GAME_STATE from server
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, screenWidth, screenHeight);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Monospaced", Font.BOLD, 22));
            String msg = "Waiting for server...";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (screenWidth - fm.stringWidth(msg)) / 2, screenHeight / 2);
            return;
        }

        updateMapFromJson(json);
        tileM.draw(g2);
        drawEaglesFromJson(g2, json);
        drawPlayersFromJson(g2, json);
        drawBulletsFromJson(g2, json);
        tileM.drawGrassOverlay(g2);
        drawNetworkHud(g2, json);

        // FPS + player info
        g2.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g2.setColor(Color.WHITE);
        g2.drawString("FPS: " + fps + "  |  " + client.playerName + " [" + client.team + "]",
                      10, 20);

        // Chat overlay (above HUD)
        chatOverlay.draw(g2, screenHeight - 26);
        if (chatInputActive) drawChatInput(g2);

        drawPlayerLeftNotif(g2);

        if (state == GameState.GAME_OVER) drawGameOverOverlay(g2);
    }

    /** Parses the "map" array from the JSON and updates tileM.mapTileNum. */
    private void updateMapFromJson(String json) {
        String mapArr = extractSection(json, "map");
        if (mapArr == null || mapArr.length() < 2) return;
        // mapArr is "[[r0c0,r0c1,...],[r1c0,...],...]"
        int pos = 1; // skip outer '['
        int row = 0;
        while (pos < mapArr.length() - 1 && row < maxScreenRow) {
            int rowStart = mapArr.indexOf('[', pos);
            if (rowStart == -1) break;
            int rowEnd = mapArr.indexOf(']', rowStart);
            if (rowEnd == -1) break;
            String[] nums = mapArr.substring(rowStart + 1, rowEnd).split(",");
            for (int col = 0; col < nums.length && col < maxScreenCol; col++) {
                try { tileM.mapTileNum[col][row] = Integer.parseInt(nums[col].trim()); }
                catch (NumberFormatException ignored) {}
            }
            row++;
            pos = rowEnd + 1;
        }
    }

    /** Reads the "eagles" array from JSON, updates the local Eagle entities, and draws them. */
    private void drawEaglesFromJson(Graphics2D g2, String json) {
        String arr = extractSection(json, "eagles");
        if (arr == null) return;
        List<String> items = splitArr(arr);
        for (String item : items) {
            String team  = parseStrField(item, "team");
            boolean alive = parseBoolField(item, "alive");
            // Map server team strings to local Eagle objects (team 1=RED, team 2=BLUE)
            for (Eagle eagle : eagles) {
                boolean match = ("RED".equals(team) && eagle.team == 1)
                             || ("BLUE".equals(team) && eagle.team == 2);
                if (match) { eagle.active = alive; eagle.draw(g2); break; }
            }
        }
    }

    /** Reads the "players" array from JSON and draws each active player as a tank sprite. */
    private void drawPlayersFromJson(Graphics2D g2, String json) {
        String arr = extractSection(json, "players");
        if (arr == null) return;
        for (String p : splitArr(arr)) {
            if (!parseBoolField(p, "active")) continue;
            int    px         = parseIntField(p, "x");
            int    py         = parseIntField(p, "y");
            String team       = parseStrField(p, "team");
            String dir        = parseStrField(p, "dir");
            String playerName = parseStrField(p, "playerName");

            boolean isRed = "RED".equals(team);
            int dirIdx;
            switch (dir) {
                case "up":   dirIdx = 0; break;
                case "down": dirIdx = 1; break;
                case "left": dirIdx = 2; break;
                default:     dirIdx = 3; break; // right
            }

            Color border = isRed ? new Color(255, 50, 50) : new Color(50, 50, 255);
            BufferedImage sprite = isRed ? redTankSprites[dirIdx] : blueTankSprites[dirIdx];
            if (sprite != null) {
                // Filled border rect 4 px outside the tank, then sprite on top
                g2.setColor(border);
                g2.fillRect(px - 4, py - 4, tileSize + 8, tileSize + 8);
                g2.drawImage(sprite, px, py, tileSize, tileSize, null);
            } else {
                // Fallback: neutral body + barrel, then 3 px border outline on top
                Color body = isRed ? new Color(200, 60, 60) : new Color(60, 100, 200);
                g2.setColor(body);
                g2.fillRect(px, py, tileSize, tileSize);
                g2.setColor(Color.YELLOW);
                int bx2 = px + tileSize / 2, by2 = py + tileSize / 2;
                int half = tileSize / 2;
                switch (dir) {
                    case "up":   g2.fillRect(bx2 - 4, py,      8, half); break;
                    case "down": g2.fillRect(bx2 - 4, by2,     8, half); break;
                    case "left": g2.fillRect(px,      by2 - 4, half, 8); break;
                    default:     g2.fillRect(bx2,     by2 - 4, half, 8); break;
                }
                g2.setColor(border);
                g2.setStroke(new BasicStroke(3));
                g2.drawRect(px, py, tileSize - 1, tileSize - 1);
                g2.setStroke(new BasicStroke(1));
            }

            // Player name above tank (white text, team-colored shadow)
            if (!playerName.isEmpty()) {
                Font nameFont = new Font("Monospaced", Font.BOLD, 9);
                g2.setFont(nameFont);
                FontMetrics nameFm = g2.getFontMetrics();
                int nameX = px + tileSize / 2 - nameFm.stringWidth(playerName) / 2;
                int nameY = py - 3;
                // Team-colored outline for readability over varied backgrounds
                Color shadow = isRed ? new Color(180, 0, 0) : new Color(0, 0, 180);
                g2.setColor(shadow);
                g2.drawString(playerName, nameX - 1, nameY - 1);
                g2.drawString(playerName, nameX + 1, nameY + 1);
                g2.setColor(Color.WHITE);
                g2.drawString(playerName, nameX, nameY);
            }
        }
    }

    /** Reads the "bullets" array from JSON and draws each bullet as a yellow oval. */
    private void drawBulletsFromJson(Graphics2D g2, String json) {
        String arr = extractSection(json, "bullets");
        if (arr == null) return;
        g2.setColor(Color.YELLOW);
        for (String b : splitArr(arr)) {
            int bx = parseIntField(b, "x");
            int by = parseIntField(b, "y");
            g2.fillOval(bx, by, 10, 10);
        }
    }

    /**
     * Draws a bottom HUD bar showing each player's name and lives per team:
     * "RED: paolo(3) vlad(2)  |  BLUE: jan(3) mike(1)"
     */
    private void drawNetworkHud(Graphics2D g2, String json) {
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRect(0, screenHeight - 22, screenWidth, 22);

        String arr = extractSection(json, "players");
        if (arr == null) return;

        StringBuilder redPart  = new StringBuilder("RED: ");
        StringBuilder bluePart = new StringBuilder("BLUE: ");
        for (String p : splitArr(arr)) {
            String name  = parseStrField(p, "playerName");
            int    lives = parseIntField(p, "lives");
            String entry = (name.isEmpty() ? "?" : name) + "(" + lives + ")";
            if ("RED".equals(parseStrField(p, "team"))) {
                if (redPart.length()  > 5) redPart.append(' ');
                redPart.append(entry);
            } else {
                if (bluePart.length() > 6) bluePart.append(' ');
                bluePart.append(entry);
            }
        }

        g2.setFont(new Font("Monospaced", Font.BOLD, 13));
        FontMetrics fm = g2.getFontMetrics();

        int x = 8;
        g2.setColor(new Color(220, 80, 80));
        g2.drawString(redPart.toString(), x, screenHeight - 6);
        x += fm.stringWidth(redPart.toString());

        g2.setColor(Color.WHITE);
        g2.drawString("  |  ", x, screenHeight - 6);
        x += fm.stringWidth("  |  ");

        g2.setColor(new Color(80, 120, 220));
        g2.drawString(bluePart.toString(), x, screenHeight - 6);
    }

    /** Draws the active chat input bar just above the HUD. */
    private void drawChatInput(Graphics2D g2) {
        String prefix = chatInputTeam ? "[TEAM] " : "[ALL] ";
        String display = prefix + chatInputBuffer.toString() + "_";
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(0, screenHeight - 44, screenWidth, 20);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g2.setColor(Color.YELLOW);
        g2.drawString(display, 8, screenHeight - 28);
    }

    /** Semi-transparent game-over overlay used in network mode. */
    private void drawGameOverOverlay(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, screenHeight / 2 - 60, screenWidth, 120);

        g2.setFont(new Font("Monospaced", Font.BOLD, 28));
        g2.setColor(Color.YELLOW);
        String msg = winMessage != null ? winMessage : "GAME OVER";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, (screenWidth - fm.stringWidth(msg)) / 2, screenHeight / 2 - 10);

        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        g2.setColor(Color.WHITE);
        fm = g2.getFontMetrics();
        if (waitingForRematch) {
            String sub = "Waiting for all players to accept rematch...";
            g2.drawString(sub, (screenWidth - fm.stringWidth(sub)) / 2, screenHeight / 2 + 18);
        } else {
            String r   = "R — Rematch";
            String m   = "M — Main Menu";
            g2.drawString(r, (screenWidth - fm.stringWidth(r)) / 2, screenHeight / 2 + 18);
            g2.drawString(m, (screenWidth - fm.stringWidth(m)) / 2, screenHeight / 2 + 38);
        }
    }

    /** Draws a 3-second disconnection notification at the top of the screen. */
    private void drawPlayerLeftNotif(Graphics2D g2) {
        if (playerLeftNotif == null) return;
        if (System.currentTimeMillis() >= playerLeftNotifUntil) {
            playerLeftNotif = null;
            return;
        }
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRect(0, 0, screenWidth, 26);
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        g2.setColor(Color.ORANGE);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(playerLeftNotif, (screenWidth - fm.stringWidth(playerLeftNotif)) / 2, 18);
    }

    // ── JSON parsing helpers (used only by network rendering) ─────────────────

    /**
     * Returns the "[...]" or "{...}" value associated with key, extracted from json.
     * Uses a balanced-bracket scan identical to Message.extractPayload.
     */
    private static String extractSection(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int start = idx + search.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;
        char first = json.charAt(start);
        if (first != '[' && first != '{') return null;
        char close = (first == '[') ? ']' : '}';
        int depth = 0;
        boolean inStr = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && inStr) { i++; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (!inStr) {
                if (c == '[' || c == '{') depth++;
                else if (c == ']' || c == '}') {
                    depth--;
                    if (depth == 0) return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /** Splits a JSON array string "[{...},{...}]" into a list of element strings. */
    private static List<String> splitArr(String arr) {
        List<String> result = new ArrayList<>();
        if (arr == null || arr.length() < 2) return result;
        int i = 1; // skip opening bracket
        while (i < arr.length() - 1) {
            char c = arr.charAt(i);
            if (c == '{' || c == '[') {
                int depth = 0;
                boolean inStr = false;
                int start = i;
                while (i < arr.length()) {
                    char ch = arr.charAt(i);
                    if (ch == '\\' && inStr) { i++; i++; continue; }
                    if (ch == '"') { inStr = !inStr; i++; continue; }
                    if (!inStr) {
                        if (ch == '[' || ch == '{') depth++;
                        else if (ch == ']' || ch == '}') {
                            depth--;
                            if (depth == 0) { result.add(arr.substring(start, i + 1)); i++; break; }
                        }
                    }
                    i++;
                }
            } else {
                i++;
            }
        }
        return result;
    }

    private static int parseIntField(String obj, String key) {
        String search = "\"" + key + "\":";
        int idx = obj.indexOf(search);
        if (idx == -1) return 0;
        int start = idx + search.length();
        int end   = start;
        if (end < obj.length() && obj.charAt(end) == '-') end++;
        while (end < obj.length() && Character.isDigit(obj.charAt(end))) end++;
        if (start == end) return 0;
        try { return Integer.parseInt(obj.substring(start, end)); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String parseStrField(String obj, String key) {
        String search = "\"" + key + "\":\"";
        int idx = obj.indexOf(search);
        if (idx == -1) return "";
        int start = idx + search.length();
        int end   = start;
        while (end < obj.length() && obj.charAt(end) != '"') {
            if (obj.charAt(end) == '\\') end++;
            end++;
        }
        return obj.substring(start, end);
    }

    private static boolean parseBoolField(String obj, String key) {
        String search = "\"" + key + "\":";
        int idx = obj.indexOf(search);
        if (idx == -1) return false;
        return obj.startsWith("true", idx + search.length());
    }

    /** Called by bullets: destroys the Eagle entity at the given world coordinate. */
    public void tryDestroyEagleAt(int worldX, int worldY) {
        int col = worldX / tileSize;
        int row = worldY / tileSize;
        for (Eagle eagle : eagles) {
            if (!eagle.active) continue;
            int eCol = eagle.x / tileSize;
            int eRow = eagle.y / tileSize;
            if (col == eCol && row == eRow) {
                eagle.active = false;
                tileM.mapTileNum[eCol][eRow] = 0; // Remove the blocking tile
            }
        }
    }

    private void exitToMainMenu() {
        if (networkMode && client != null) {
            client.disconnect();
            client = null;
        }
        networkMode       = false;
        isHost            = false;
        winMessage        = null;
        pausedNetworkJson = null;
        waitingForRematch = false;
        playerLeftNotif   = null;
        projectileList.clear();
        player.lives  = 3;
        player.score  = 0;
        player.setDefaultValues();
        player2.lives = 3;
        player2.score = 0;
        player2.setDefaultValues();
        for (Eagle eagle : eagles) eagle.active = true;
        tileM.loadMap("/maps/map1.txt");
        menuScreen.resetToMain();
        keyH.resetAll();
        state = GameState.MENU;
    }

    public void start() {
        running = true;
        gameThread = new Thread(this, "game-loop");
        gameThread.start();
    }

    @Override
    public void run() {
        final long   FRAME_NS      = 1_000_000_000L / Constants.TARGET_FPS;
        final double NS_PER_UPDATE = (double) FRAME_NS;
        long prevTime  = System.nanoTime();
        double delta   = 0;
        long fpsTimer  = System.currentTimeMillis();
        int frameCount = 0;

        while (running) {
            long frameStart = System.nanoTime();
            delta += (double)(frameStart - prevTime) / NS_PER_UPDATE;
            prevTime = frameStart;

            while (delta >= 1) {
                update();
                delta--;
            }

            repaint();
            frameCount++;

            if (System.currentTimeMillis() - fpsTimer >= 1000) {
                fps        = frameCount;
                frameCount = 0;
                fpsTimer  += 1000;
            }

            long sleepNs = FRAME_NS - (System.nanoTime() - frameStart);
            if (sleepNs > 0) {
                try {
                    Thread.sleep(sleepNs / 1_000_000, (int)(sleepNs % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void checkWinConditions() {
        for (Eagle eagle : eagles) {
            if (!eagle.active) {
                int winner = (eagle.team == 1) ? 2 : 1;
                winMessage = "TEAM " + winner + " WINS!  Eagle destroyed!";
                state = GameState.GAME_OVER;
                return;
            }
        }
        if (!player.active  && player.lives  == 0) {
            winMessage = "TEAM 2 WINS!  All enemies eliminated!";
            state = GameState.GAME_OVER;
        } else if (!player2.active && player2.lives == 0) {
            winMessage = "TEAM 1 WINS!  All enemies eliminated!";
            state = GameState.GAME_OVER;
        }
    }

    private void resetGame() {
        winMessage = null;
        projectileList.clear();

        player.lives  = 3;
        player.score  = 0;
        player.setDefaultValues();

        player2.lives = 3;
        player2.score = 0;
        player2.setDefaultValues();

        for (Eagle eagle : eagles) eagle.active = true;

        tileM.loadMap("/maps/map1.txt");

        state = GameState.PLAYING;
    }

    public void update() {
        if (state == GameState.MENU) {
            if (networkMode && client != null) {
                // Waiting room: check for disconnect or game start
                if (!client.isConnected()) {
                    networkMode = false;
                    isHost      = false;
                    client      = null;
                    menuScreen.resetToMain();
                } else if (client.getLatestState() != null) {
                    // Server has started sending game state — begin playing
                    state = GameState.PLAYING;
                } else if (keyH.enterPressed || keyH.spacePressed) {
                    keyH.enterPressed = false;
                    keyH.spacePressed = false;
                    if (isHost) client.sendInput("START");
                }
                return;
            }
            // Normal local menu — only start game from the top-level MAIN sub-state
            if (keyH.enterPressed || keyH.spacePressed) {
                keyH.enterPressed = false;
                keyH.spacePressed = false;
                if (menuScreen.isAtMain()) {
                    networkMode = false;
                    state = GameState.PLAYING;
                }
            }
            return;
        }

        // Skip all game logic while paused (ESC key listener handles state transitions)
        if (state == GameState.PAUSED) return;

        // Network mode: server owns all game logic; client just sends input
        if (networkMode) {
            updateNetwork();
            return;
        }

        if (state == GameState.GAME_OVER) {
            if (keyH.enterPressed || keyH.spacePressed) {
                keyH.enterPressed = false;
                keyH.spacePressed = false;
                resetGame();
            }
            return;
        }

        // PLAYING (local)
        int bulletCount = projectileList.size();
        player.update();
        player2.update();

        for (int i = 0; i < bulletCount; i++) {
            Projectile proj = projectileList.get(i);
            if (proj.alive) {
                proj.update();
            } else {
                projectileList.remove(i);
                i--;
                bulletCount--;
            }
        }
        checkWinConditions();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        if (state == GameState.MENU) {
            menuScreen.draw(g2);
            g2.dispose();
            return;
        }

        // Network mode: render from latest server-state JSON (or frozen state when paused)
        if (networkMode && client != null) {
            paintNetworkFrame(g2);
            if (state == GameState.PAUSED) pauseMenu.draw(g2, screenWidth, screenHeight);
            g2.dispose();
            return;
        }

        // Local rendering
        tileM.draw(g2);
        for (Eagle eagle : eagles) eagle.draw(g2);
        player.draw(g2);
        player2.draw(g2);

        for (int i = 0; i < projectileList.size(); i++) {
            if (projectileList.get(i) != null) {
                projectileList.get(i).draw(g2);
            }
        }

        tileM.drawGrassOverlay(g2);
        hud.draw(g2);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g2.setColor(Color.WHITE);
        g2.drawString("FPS: " + fps, 10, 20);

        if (state == GameState.PAUSED) pauseMenu.draw(g2, screenWidth, screenHeight);

        g2.dispose();
    }
}
