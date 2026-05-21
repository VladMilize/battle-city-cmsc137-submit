package network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Handles one connected client on its own thread.
 * Reads newline-delimited JSON messages and forwards them to the GameServer.
 *
 * The accept loop reads the JOIN line using a BufferedReader before constructing
 * this handler; that same reader is passed in so no buffered bytes are lost.
 */
public class ClientHandler implements Runnable {

    final Socket     socket;
    final int        playerId;
    final String     team;
    final GameServer server;

    String playerName = "Player";

    private final BufferedReader  preIn;
    private final PrintWriter     out;
    private volatile boolean      connected = true;

    public ClientHandler(Socket socket, BufferedReader in, int playerId,
                         String team, String playerName, GameServer server)
            throws IOException {
        this.socket     = socket;
        this.preIn      = in;
        this.playerId   = playerId;
        this.team       = team;
        this.playerName = (playerName != null && !playerName.isEmpty()) ? playerName : "Player";
        this.server     = server;
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    @Override
    public void run() {
        // Use the reader that was already created in the accept loop so no
        // buffered bytes are dropped (the JOIN line was consumed there).
        try {
            String line;
            while (connected && (line = preIn.readLine()) != null) {
                Message msg = Message.fromJson(line.trim());
                if (msg != null) server.handleMessage(this, msg);
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("[Client " + playerId + "] read error: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    /** Writes msg.toJson() + newline to the client. Thread-safe. */
    public synchronized void sendMessage(Message m) {
        if (connected && out != null) {
            out.println(m.toJson());
        }
    }

    /** Closes the socket and notifies the server. Idempotent. */
    public void disconnect() {
        if (!connected) return;
        connected = false;
        try { socket.close(); } catch (IOException ignored) {}
        server.clientDisconnected(this);
    }
}
