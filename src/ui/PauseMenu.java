package ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

public class PauseMenu {

    private static final String[] OPTIONS = { "RESUME", "EXIT TO MAIN MENU" };

    public int selectedOption = 0; // 0 = RESUME, 1 = EXIT TO MAIN MENU

    public void draw(Graphics2D g, int screenW, int screenH) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, screenW, screenH);

        int cx = screenW / 2;
        int cy = screenH / 2;

        g.setFont(new Font("Monospaced", Font.BOLD, 32));
        g.setColor(Color.YELLOW);
        String title = "PAUSED";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, cx - fm.stringWidth(title) / 2, cy - 60);

        g.setFont(new Font("Monospaced", Font.BOLD, 20));
        for (int i = 0; i < OPTIONS.length; i++) {
            fm = g.getFontMetrics();
            String label = (i == selectedOption ? "> " : "  ") + OPTIONS[i];
            g.setColor(i == selectedOption ? Color.WHITE : new Color(160, 160, 160));
            g.drawString(label, cx - fm.stringWidth(label) / 2, cy + i * 40);
        }

        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g.setColor(new Color(140, 140, 140));
        String hint = "UP/DOWN — select   ENTER — confirm   ESC — resume";
        fm = g.getFontMetrics();
        g.drawString(hint, cx - fm.stringWidth(hint) / 2, cy + 100);
    }

    public void moveUp()   { if (selectedOption > 0) selectedOption--; }
    public void moveDown() { if (selectedOption < OPTIONS.length - 1) selectedOption++; }
}
