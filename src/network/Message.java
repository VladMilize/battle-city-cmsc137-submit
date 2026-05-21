package network;

public class Message {
    public final MessageType type;
    public final String payload; // JSON object string

    public Message(MessageType type, String payload) {
        this.type = type;
        this.payload = payload != null ? payload : "{}";
    }

    /** Returns the full message as a JSON string: {"type":"...","payload":{...}} */
    public String toJson() {
        return "{\"type\":\"" + type.name() + "\",\"payload\":" + payload + "}";
    }

    /** Parses a JSON string produced by toJson() and returns a Message. */
    public static Message fromJson(String json) {
        if (json == null || json.isEmpty()) return null;

        MessageType type = extractType(json);
        String payload = extractPayload(json);

        if (type == null) return null;
        return new Message(type, payload);
    }

    private static MessageType extractType(String json) {
        String key = "\"type\":\"";
        int start = json.indexOf(key);
        if (start == -1) return null;
        start += key.length();
        int end = json.indexOf('"', start);
        if (end == -1) return null;
        String name = json.substring(start, end);
        try {
            return MessageType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Extracts the JSON value after "payload": handling nested braces/brackets.
    private static String extractPayload(String json) {
        String key = "\"payload\":";
        int idx = json.indexOf(key);
        if (idx == -1) return "{}";
        int start = idx + key.length();

        // Skip whitespace
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return "{}";

        char first = json.charAt(start);
        if (first == '{' || first == '[') {
            char close = (first == '{') ? '}' : ']';
            int depth = 0;
            boolean inString = false;
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\' && inString) {
                    i++; // skip escaped char
                    continue;
                }
                if (c == '"') {
                    inString = !inString;
                    continue;
                }
                if (!inString) {
                    if (c == first) depth++;
                    else if (c == close) {
                        depth--;
                        if (depth == 0) return json.substring(start, i + 1);
                    }
                }
            }
        }
        return "{}";
    }
}
