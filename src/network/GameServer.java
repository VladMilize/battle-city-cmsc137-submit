package network;

import core.Constants;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Authoritative game server.
 *
 * Lobby:   accepts up to maxPlayers clients.  Client sends JOIN with name+team;
 *          server validates team capacity (max 2 per team) and replies JOIN_ACK.
 * Auto-start: when maxPlayers is reached OR 30 seconds after the server opens,
 *             whichever comes first (requires ≥2 players).
 * Game loop: fixed 60-UPS server loop; broadcasts GAME_STATE every tick.
 */
public class GameServer {

    private ServerSocket serverSocket;

    private final List<ClientHandler> clients =
            Collections.synchronizedList(new ArrayList<>());

    private final int maxPlayers = Constants.MAX_PLAYERS;

    private volatile boolean gameStarted       = false;
    private volatile boolean acceptingPlayers  = true;
    private volatile boolean shutdownRequested = false;

    private volatile ServerGameState gameState;

    private final Set<Integer> rematchVotes =
            Collections.synchronizedSet(new HashSet<>());

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        GameServer server = new GameServer();
        try {
            server.start(5000);
        } catch (IOException e) {
            System.err.println("[GameServer] Fatal: " + e.getMessage());
        }
    }

    // ── Server start / accept loop ────────────────────────────────────────────

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server listening on port " + port);

        Thread autoStart = new Thread(() -> {
            try { Thread.sleep(30_000); } catch (InterruptedException e) { return; }
            synchronized (clients) {
                if (!gameStarted && clients.size() >= 2) startGame();
            }
        }, "auto-start-timer");
        autoStart.setDaemon(true);
        autoStart.start();

        while (acceptingPlayers) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (!acceptingPlayers) break;
                System.err.println("[GameServer] accept error: " + e.getMessage());
                continue;
            }

            // Read the JOIN message BEFORE entering the synchronized block so we
            // don't hold the clients lock during blocking I/O.
            BufferedReader joinReader;
            String joinLine;
            try {
                socket.setSoTimeout(5000); // fail fast if client never sends JOIN
                joinReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                joinLine   = joinReader.readLine();
                socket.setSoTimeout(0);
                if (joinLine == null) throw new IOException("no data");
            } catch (IOException e) {
                System.err.println("[GameServer] Failed to read JOIN: " + e.getMessage());
                try { socket.close(); } catch (IOException ignored) {}
                continue;
            }

            Message joinMsg = Message.fromJson(joinLine.trim());
            String reqTeam  = (joinMsg != null) ? extractString(joinMsg.payload, "team")  : "";
            String pName    = (joinMsg != null) ? extractString(joinMsg.payload, "name")  : "";

            if (pName  == null || pName.isEmpty())  pName  = "Player";
            // Normalise team; null means "auto-assign"
            if (!"RED".equals(reqTeam) && !"BLUE".equals(reqTeam)) reqTeam = null;

            synchronized (clients) {
                if (gameStarted || clients.size() >= maxPlayers) {
                    try { socket.close(); } catch (IOException ignored) {}
                    continue;
                }

                // Count members per team
                int redCount = 0, blueCount = 0;
                for (ClientHandler c : clients) {
                    if ("RED".equals(c.team)) redCount++;
                    else blueCount++;
                }

                // Validate or auto-assign team
                String team;
                if (reqTeam == null) {
                    team = (redCount <= blueCount) ? "RED" : "BLUE";
                } else if ("RED".equals(reqTeam) && redCount >= 2) {
                    sendRejection(socket, "RED", "Team full");
                    continue;
                } else if ("BLUE".equals(reqTeam) && blueCount >= 2) {
                    sendRejection(socket, "BLUE", "Team full");
                    continue;
                } else {
                    team = reqTeam;
                }

                int playerId = clients.size();
                try {
                    ClientHandler handler = new ClientHandler(
                            socket, joinReader, playerId, team, pName, this);
                    handler.sendMessage(Protocol.buildJoinAck(playerId, team, true, ""));
                    clients.add(handler);
                    new Thread(handler, "client-" + playerId).start();
                    System.out.println("Client " + playerId + " connected (total: " + clients.size() + ")");

                    broadcastAll(Protocol.buildLobbyStatus(
                            clients.size(), maxPlayers, NetworkUtils.getLanIp()));

                    if (clients.size() == maxPlayers) startGame();
                } catch (IOException e) {
                    System.err.println("[GameServer] Failed to set up client: " + e.getMessage());
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }
        }
    }

    // ── Game start ────────────────────────────────────────────────────────────

    private void startGame() {
        if (gameStarted) return;
        gameStarted      = true;
        acceptingPlayers = false;

        int      n     = clients.size();
        String[] teams = new String[n];
        String[] names = new String[n];
        for (int i = 0; i < n; i++) {
            teams[i] = clients.get(i).team;
            names[i] = clients.get(i).playerName;
        }

        gameState = new ServerGameState(n, teams, names);
        System.out.println("Starting game with " + n + " players");

        try { serverSocket.close(); } catch (IOException ignored) {}

        Thread loop = new Thread(this::runGameLoop, "server-game-loop");
        loop.setDaemon(true);
        loop.start();
    }

    // ── Server game loop (60 UPS fixed timestep) ──────────────────────────────

    private void runGameLoop() {
        System.out.println("Game loop started");
        final long TICK_NS = 1_000_000_000L / 60;
        long prev  = System.nanoTime();
        double delta = 0.0;

        while (true) {
            if (shutdownRequested) break;

            long now = System.nanoTime();
            delta += (double)(now - prev) / TICK_NS;
            prev = now;

            while (delta >= 1.0) {
                gameState.update();
                delta -= 1.0;
            }

            broadcastAll(Protocol.buildGameState(gameState.toJson()));

            if (gameState.gameOver) {
                broadcastAll(Protocol.buildGameOver(gameState.winnerTeam));
                break;
            }

            long sleepNs = TICK_NS - (System.nanoTime() - now);
            if (sleepNs > 0) {
                try {
                    Thread.sleep(sleepNs / 1_000_000, (int)(sleepNs % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // ── Message handling ──────────────────────────────────────────────────────

    public void handleMessage(ClientHandler sender, Message m) {
        switch (m.type) {
            case INPUT:
                String keys = extractString(m.payload, "keysHeld");
                if (gameStarted && "REMATCH".equals(keys)) {
                    handleRematchVote(sender.playerId);
                } else if (gameStarted) {
                    ServerGameState gs = gameState;
                    if (gs != null) gs.applyInput(sender.playerId, keys != null ? keys : "");
                } else if (sender.playerId == 0 && "START".equals(keys)) {
                    synchronized (clients) {
                        if (!gameStarted && clients.size() >= 2) startGame();
                    }
                }
                break;

            case CHAT:
                String scope = extractString(m.payload, "scope");
                if ("TEAM".equalsIgnoreCase(scope)) {
                    broadcastTeam(m, sender.team);
                } else {
                    broadcastAll(m);
                }
                break;

            default:
                break;
        }
    }

    // ── Broadcast helpers ─────────────────────────────────────────────────────

    public void broadcastAll(Message m) {
        synchronized (clients) {
            Iterator<ClientHandler> it = clients.iterator();
            while (it.hasNext()) {
                ClientHandler c = it.next();
                if (c != null) c.sendMessage(m);
            }
        }
    }

    public void broadcastTeam(Message m, String team) {
        synchronized (clients) {
            Iterator<ClientHandler> it = clients.iterator();
            while (it.hasNext()) {
                ClientHandler c = it.next();
                if (c != null && team != null && team.equals(c.team)) c.sendMessage(m);
            }
        }
    }

    // ── Disconnect ────────────────────────────────────────────────────────────

    public void clientDisconnected(ClientHandler c) {
        synchronized (clients) {
            clients.remove(c);
            int remaining = clients.size();
            System.out.println("Client " + c.playerId + " disconnected (total: " + remaining + " remaining)");

            if (!gameStarted) {
                broadcastAll(Protocol.buildLobbyStatus(
                        remaining, maxPlayers, NetworkUtils.getLanIp()));
            } else {
                ServerGameState gs = gameState;
                if (gs != null && !gs.gameOver) {
                    // Mark disconnected but preserve lives — avoids false win-condition trigger
                    gs.markDisconnected(c.playerId);
                    broadcastAll(Protocol.buildPlayerLeft(c.playerName, c.team));
                }
                if (remaining == 0) shutdownRequested = true;
            }
        }
    }

    private void handleRematchVote(int playerId) {
        rematchVotes.add(playerId);
        synchronized (clients) {
            ServerGameState gs = gameState;
            if (gs != null && gs.gameOver && !clients.isEmpty()
                    && rematchVotes.size() >= clients.size()) {
                rematchVotes.clear();
                gs.reset();
                broadcastAll(Protocol.buildRematchReady());
                Thread loop = new Thread(this::runGameLoop, "server-game-loop");
                loop.setDaemon(true);
                loop.start();
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Sends a JOIN_ACK rejection and closes the socket. Called inside synchronized(clients). */
    private static void sendRejection(Socket socket, String team, String reason) {
        try {
            PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
            pw.println(Protocol.buildJoinAck(-1, team, false, reason).toJson());
            socket.close();
        } catch (IOException ignored) {
            try { socket.close(); } catch (IOException e2) {}
        }
    }

    /** Extracts the string value for `key` from a flat JSON object string. */
    private static String extractString(String json, String key) {
        if (json == null) return null;
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int start = idx + search.length();
        int end   = json.indexOf('"', start);
        return (end == -1) ? null : json.substring(start, end);
    }
}
