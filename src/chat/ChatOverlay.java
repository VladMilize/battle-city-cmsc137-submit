package chat;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Renders up to 6 recent chat messages at the bottom-left of the screen.
 * Messages fade out after 8 seconds.
 */
public class ChatOverlay {

    private static final int  MAX_MESSAGES = 6;
    private static final long FADE_MS      = 8_000;
    private static final long FADE_RAMP_MS = 2_000; // last 2 s of fade

    private static class Entry {
        final String text;
        final long   timestamp;
        Entry(String text) { this.text = text; this.timestamp = System.currentTimeMillis(); }
    }

    // newest first
    private final Deque<Entry> messages = new ArrayDeque<>();

    /** Clears all chat messages, e.g. on rematch. */
    public void clear() {
        synchronized (messages) { messages.clear(); }
    }

    /** Adds a chat message prefixed with [TEAM] or [ALL]. Thread-safe. */
    public void addMessage(String sender, String text, String scope) {
        String label = "TEAM".equalsIgnoreCase(scope) ? "[TEAM]" : "[ALL]";
        String full  = label + " " + sender + ": " + text;
        synchronized (messages) {
            messages.addFirst(new Entry(full));
            while (messages.size() > MAX_MESSAGES) messages.removeLast();
        }
    }

    /**
     * Draws visible chat messages.
     * @param g       Graphics2D context
     * @param bottomY pixel Y of the bottom edge of the chat area (above HUD)
     */
    public void draw(Graphics2D g, int bottomY) {
        Font font = new Font("Monospaced", Font.PLAIN, 13);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int lineH = fm.getHeight() + 2;

        long now = System.currentTimeMillis();

        List<Entry> snapshot;
        synchronized (messages) {
            snapshot = new ArrayList<>(messages);
        }

        // Collect entries that are still visible (not fully faded)
        List<Entry> visible = new ArrayList<>();
        for (Entry e : snapshot) {
            if (now - e.timestamp < FADE_MS) visible.add(e);
        }
        if (visible.isEmpty()) return;

        // Draw oldest-to-newest from top to bottom so newest is at the bottom
        // visible[0] = newest, visible[last] = oldest
        for (int i = visible.size() - 1; i >= 0; i--) {
            Entry e = visible.get(i);
            long age = now - e.timestamp;
            float alpha;
            if (age < FADE_MS - FADE_RAMP_MS) {
                alpha = 1.0f;
            } else {
                alpha = (float)(FADE_MS - age) / FADE_RAMP_MS;
            }
            alpha = Math.max(0f, Math.min(1f, alpha));

            // oldest entry is drawn highest; newest is closest to bottomY
            int rank  = visible.size() - 1 - i; // 0 = newest (bottom), last = oldest (top)
            int msgY  = bottomY - rank * lineH;
            int msgW  = fm.stringWidth(e.text) + 10;

            g.setColor(new Color(0f, 0f, 0f, alpha * 0.75f));
            g.fillRect(5, msgY - fm.getAscent() - 2, msgW, lineH);

            g.setColor(new Color(1f, 1f, 1f, alpha));
            g.drawString(e.text, 10, msgY);
        }
    }
}
