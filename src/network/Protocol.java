package network;

public class Protocol {

    private Protocol() {}

    public static Message buildJoin(String playerName, String team) {
        String payload = "{\"name\":\"" + escape(playerName) + "\",\"team\":\"" + escape(team) + "\"}";
        return new Message(MessageType.JOIN, payload);
    }

    public static Message buildJoinAck(int playerId, String team, boolean accepted, String reason) {
        String payload = "{\"playerId\":" + playerId + ",\"team\":\"" + escape(team)
                + "\",\"accepted\":" + accepted
                + ",\"reason\":\"" + escape(reason != null ? reason : "") + "\"}";
        return new Message(MessageType.JOIN_ACK, payload);
    }

    /** keysHeld is a comma-separated string of held keys, e.g. "UP,SHOOT" */
    public static Message buildInput(int playerId, String keysHeld) {
        String payload = "{\"playerId\":" + playerId + ",\"keysHeld\":\"" + escape(keysHeld) + "\"}";
        return new Message(MessageType.INPUT, payload);
    }

    /** stateJson is a complete JSON object representing the current game state. */
    public static Message buildGameState(String stateJson) {
        return new Message(MessageType.GAME_STATE, stateJson != null ? stateJson : "{}");
    }

    public static Message buildLobbyStatus(int connected, int max, String hostIp) {
        String payload = "{\"connected\":" + connected + ",\"max\":" + max
                + ",\"hostIp\":\"" + escape(hostIp) + "\"}";
        return new Message(MessageType.LOBBY_STATUS, payload);
    }

    public static Message buildGameOver(String winnerTeam) {
        String payload = "{\"winnerTeam\":\"" + escape(winnerTeam) + "\"}";
        return new Message(MessageType.GAME_OVER, payload);
    }

    public static Message buildPlayerLeft(String playerName, String team) {
        String payload = "{\"playerName\":\"" + escape(playerName) + "\",\"team\":\"" + escape(team) + "\"}";
        return new Message(MessageType.PLAYER_LEFT, payload);
    }

    public static Message buildRematch() {
        return new Message(MessageType.REMATCH, "{}");
    }

    public static Message buildRematchReady() {
        return new Message(MessageType.REMATCH_READY, "{}");
    }

    /** scope: "ALL" or "TEAM" */
    public static Message buildChat(String playerName, int playerId, String message, String scope) {
        String payload = "{\"playerName\":\"" + escape(playerName) + "\",\"playerId\":" + playerId
                + ",\"message\":\"" + escape(message) + "\",\"scope\":\"" + escape(scope) + "\"}";
        return new Message(MessageType.CHAT, payload);
    }

    /** Escapes special characters for embedding in a JSON string value. */
    static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

}
