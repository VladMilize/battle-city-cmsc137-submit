package network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Connects to a GameServer, sends player input, and receives game-state updates.
 */
public class GameClient {

    // ── Connection state ──────────────────────────────────────────────────────

    private Socket       socket;
    private BufferedReader in;
    private PrintWriter  out;

    public int     playerId   = -1;
    public String  team       = "";
    public String  playerName = "Player";
    private volatile boolean connected = false;

    // ── Game state (written by listener thread, read by game loop / Swing EDT) ─

    private volatile String  latestState       = null; // raw JSON payload from last GAME_STATE
    private volatile boolean gameOver          = false;
    private volatile String  winner            = "";
    private volatile String  playerLeftMessage = null;
    private volatile boolean rematchReady      = false;

    // ── Lobby state (updated from LOBBY_STATUS broadcasts) ───────────────────

    private volatile int    lobbyConnected = 0;
    private volatile String lobbyHostIp    = "";

    // ── Chat callback ─────────────────────────────────────────────────────────

    private volatile core.GamePanel gamePanel = null;

    public void setGamePanel(core.GamePanel p) { this.gamePanel = p; }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Opens a TCP connection to host:port, sends a JOIN, waits for JOIN_ACK,
     * stores the assigned playerId and team, then starts a background listener thread.
     * Throws IOException if the connection fails or the server rejects the join.
     */
    public void connect(String host, int port, String playerName, String preferredTeam)
            throws IOException {
        socket = new Socket(host, port);
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        // Remember the name we used so chat messages carry it
        this.playerName = (playerName != null && !playerName.isEmpty()) ? playerName : "Player";

        // Send JOIN
        String pref = (preferredTeam != null) ? preferredTeam : "";
        out.println(Protocol.buildJoin(this.playerName, pref).toJson());

        // Block until we get JOIN_ACK (first message from server)
        String ackLine = in.readLine();
        if (ackLine == null) {
            close();
            throw new IOException("Server closed connection before JOIN_ACK");
        }
        Message ack = Message.fromJson(ackLine.trim());
        if (ack == null || ack.type != MessageType.JOIN_ACK) {
            close();
            throw new IOException("Expected JOIN_ACK, got: " + ackLine);
        }

        boolean accepted = extractBool(ack.payload, "accepted");
        if (!accepted) {
            String reason = extractStr(ack.payload, "reason");
            close();
            throw new IOException(reason.isEmpty() ? "Server rejected join" : reason);
        }

        playerId  = extractInt(ack.payload, "playerId");
        team      = extractStr(ack.payload, "team");
        connected = true;

        // Start background listener
        Thread listener = new Thread(this::listenLoop, "net-listener-" + playerId);
        listener.setDaemon(true);
        listener.start();
    }

    /** Sends the current held-key set to the server. */
    public void sendInput(String keysHeld) {
        send(Protocol.buildInput(playerId, keysHeld));
    }

    /** Sends a chat message with the given scope ("ALL" or "TEAM"). */
    public void sendChat(String text, String scope) {
        send(Protocol.buildChat(playerName, playerId, text, scope));
    }

    /** Closes the socket and marks the client as disconnected. */
    public void disconnect() {
        connected = false;
        close();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** Returns the raw JSON payload of the most recent GAME_STATE message, or null. */
    public String getLatestState() { return latestState; }

    public boolean isConnected()         { return connected;          }
    public boolean isGameOver()          { return gameOver;           }
    public String  getWinner()           { return winner;             }
    public int     getLobbyConnected()   { return lobbyConnected;     }
    public String  getLobbyHostIp()      { return lobbyHostIp;        }
    public String  getPlayerLeftMessage() { return playerLeftMessage; }
    public void    clearPlayerLeftMessage() { playerLeftMessage = null; }
    public boolean isRematchReady()      { return rematchReady;       }
    /** Clears rematch-ready flag and resets game-over state so the client can play again. */
    public void    clearRematchReady() {
        rematchReady = false;
        gameOver     = false;
        winner       = "";
        latestState  = null;
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    private void listenLoop() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                Message msg = Message.fromJson(line.trim());
                if (msg != null) onMessage(msg);
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("[GameClient] Listener error: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    private void onMessage(Message m) {
        switch (m.type) {
            case LOBBY_STATUS:
                lobbyConnected = extractInt(m.payload, "connected");
                lobbyHostIp    = extractStr(m.payload, "hostIp");
                break;

            case GAME_STATE:
                latestState = m.payload;
                break;

            case GAME_OVER:
                winner   = extractStr(m.payload, "winnerTeam");
                gameOver = true;
                break;

            case PLAYER_LEFT:
                String leftName = extractStr(m.payload, "playerName");
                playerLeftMessage = (leftName.isEmpty() ? "A player" : leftName) + " disconnected";
                break;

            case REMATCH_READY:
                rematchReady = true;
                break;

            case CHAT:
                core.GamePanel gp = gamePanel;
                if (gp != null && gp.chatOverlay != null) {
                    String pName  = extractStr(m.payload, "playerName");
                    String sender = pName.isEmpty() ? ("P" + extractInt(m.payload, "playerId")) : pName;
                    String text   = extractStr(m.payload, "message");
                    String scope  = extractStr(m.payload, "scope");
                    gp.chatOverlay.addMessage(sender, text, scope);
                }
                break;

            default:
                break;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void send(Message m) {
        if (connected && out != null) out.println(m.toJson());
    }

    private void close() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    // Minimal JSON field extractors — same approach as Message.fromJson()

    private static int extractInt(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return 0;
        int start = idx + search.length();
        int end   = start;
        if (end < json.length() && json.charAt(end) == '-') end++;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (start == end) return 0;
        try { return Integer.parseInt(json.substring(start, end)); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String extractStr(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx == -1) return "";
        int start = idx + search.length();
        int end   = start;
        while (end < json.length() && json.charAt(end) != '"') {
            if (json.charAt(end) == '\\') end++; // skip escaped char
            end++;
        }
        return json.substring(start, end);
    }

    private static boolean extractBool(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return false;
        return json.startsWith("true", idx + search.length());
    }
}
