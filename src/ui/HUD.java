package ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import core.GamePanel;

public class HUD {

    GamePanel gp;
    private static final Font HUD_FONT = new Font("Monospaced", Font.BOLD, 14);
    private static final Font WIN_FONT = new Font("Monospaced", Font.BOLD, 28);

    public HUD(GamePanel gp) {
        this.gp = gp;
    }

    public void draw(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(HUD_FONT);
        FontMetrics fm = g2.getFontMetrics();

        // Semi-transparent bar at the bottom
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRect(0, gp.screenHeight - 22, gp.screenWidth, 22);

        // Team 1 — left, cyan
        g2.setColor(new Color(0, 220, 220));
        String p1 = "TEAM 1  Lives: " + gp.player.lives + "  Score: " + gp.player.score;
        g2.drawString(p1, 8, gp.screenHeight - 6);

        // Team 2 — right, orange
        g2.setColor(new Color(255, 165, 0));
        String p2 = "TEAM 2  Lives: " + gp.player2.lives + "  Score: " + gp.player2.score;
        g2.drawString(p2, gp.screenWidth - fm.stringWidth(p2) - 8, gp.screenHeight - 6);

        // Game over overlay
        if (gp.winMessage != null) {
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRect(0, gp.screenHeight / 2 - 54, gp.screenWidth, 108);

            g2.setFont(WIN_FONT);
            g2.setColor(Color.YELLOW);
            FontMetrics wfm = g2.getFontMetrics();
            int tx = (gp.screenWidth - wfm.stringWidth(gp.winMessage)) / 2;
            g2.drawString(gp.winMessage, tx, gp.screenHeight / 2);

            g2.setFont(HUD_FONT);
            g2.setColor(Color.WHITE);
            String restart = "Press ENTER or SPACE to restart";
            fm = g2.getFontMetrics();
            g2.drawString(restart, (gp.screenWidth - fm.stringWidth(restart)) / 2,
                          gp.screenHeight / 2 + 30);
        }
    }
}
