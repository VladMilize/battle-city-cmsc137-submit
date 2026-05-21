package ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import core.GamePanel;
import network.NetworkUtils;

public class MenuScreen {

    private enum SubState { MAIN, NAME_INPUT, IP_INPUT, TEAM_SELECT }

    private final GamePanel gp;
    private static final Font TITLE_FONT  = new Font("Monospaced", Font.BOLD,  48);
    private static final Font CTRL_FONT   = new Font("Monospaced", Font.PLAIN, 16);
    private static final Font PROMPT_FONT = new Font("Monospaced", Font.BOLD,  18);
    private static final Font INPUT_FONT  = new Font("Monospaced", Font.BOLD,  20);

    private SubState subState    = SubState.MAIN;
    private boolean  isHostFlow  = false;
    private final StringBuilder inputBuffer = new StringBuilder();
    private String pendingName   = "";
    private String pendingIp     = "";
    private String teamErrorMsg  = "";

    public MenuScreen(GamePanel gp) {
        this.gp = gp;
    }

    // ── Public interface ──────────────────────────────────────────────────────

    /**
     * Called (via SwingUtilities.invokeLater) from background threads when the
     * server rejects a team selection.  Resets to TEAM_SELECT with an error.
     */
    public void showTeamFullError(String msg) {
        teamErrorMsg = msg;
        subState     = SubState.TEAM_SELECT;
    }

    /** True only when sitting at the top-level MAIN menu (no text-input sub-state active). */
    public boolean isAtMain() {
        return subState == SubState.MAIN;
    }

    /** Resets to the main menu, e.g. after a network disconnect. */
    public void resetToMain() {
        subState     = SubState.MAIN;
        teamErrorMsg = "";
        inputBuffer.setLength(0);
    }

    /** Routes a key event to the active sub-state handler. */
    public void handleKey(KeyEvent e) {
        switch (subState) {
            case MAIN:        handleMainKey(e);        break;
            case NAME_INPUT:  handleInputKey(e, 12);   break;
            case IP_INPUT:    handleInputKey(e, 64);   break;
            case TEAM_SELECT: handleTeamKey(e);        break;
        }
    }

    // ── Key handlers per sub-state ────────────────────────────────────────────

    private void handleMainKey(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_L) {
            gp.startLocalGame();
        } else if (code == KeyEvent.VK_H) {
            isHostFlow = true;
            teamErrorMsg = "";
            inputBuffer.setLength(0);
            subState = SubState.NAME_INPUT;
        } else if (code == KeyEvent.VK_J) {
            isHostFlow = false;
            teamErrorMsg = "";
            inputBuffer.setLength(0);
            subState = SubState.NAME_INPUT;
        }
    }

    private void handleInputKey(KeyEvent e, int maxLen) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_ENTER) {
            String value = inputBuffer.toString().trim();
            if (value.isEmpty()) return;
            inputBuffer.setLength(0);
            if (subState == SubState.NAME_INPUT) {
                pendingName = value;
                if (isHostFlow) {
                    teamErrorMsg = "";
                    subState = SubState.TEAM_SELECT;
                } else {
                    subState = SubState.IP_INPUT;
                }
            } else { // IP_INPUT
                pendingIp    = value;
                teamErrorMsg = "";
                subState     = SubState.TEAM_SELECT;
            }
        } else if (code == KeyEvent.VK_ESCAPE) {
            inputBuffer.setLength(0);
            subState = (subState == SubState.NAME_INPUT) ? SubState.MAIN : SubState.NAME_INPUT;
        } else if (code == KeyEvent.VK_BACK_SPACE) {
            if (inputBuffer.length() > 0) inputBuffer.deleteCharAt(inputBuffer.length() - 1);
        } else {
            char c = e.getKeyChar();
            if (c != KeyEvent.CHAR_UNDEFINED && c >= 32 && c < 127 && inputBuffer.length() < maxLen) {
                inputBuffer.append(c);
            }
        }
    }

    private void handleTeamKey(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_R) {
            commitTeam("RED");
        } else if (code == KeyEvent.VK_B) {
            commitTeam("BLUE");
        } else if (code == KeyEvent.VK_ESCAPE) {
            teamErrorMsg = "";
            inputBuffer.setLength(0);
            subState = isHostFlow ? SubState.NAME_INPUT : SubState.IP_INPUT;
        }
    }

    private void commitTeam(String team) {
        teamErrorMsg = "";
        // Go back to MAIN; the networkMode waiting-room will take over once connected.
        subState = SubState.MAIN;
        if (isHostFlow) {
            gp.startHostGame(pendingName, team);
        } else {
            gp.startJoinGame(pendingIp, pendingName, team);
        }
    }

    // ── Draw routing ──────────────────────────────────────────────────────────

    public void draw(Graphics2D g2) {
        if (gp.networkMode && gp.client != null) {
            drawWaitingRoom(g2);
            return;
        }
        switch (subState) {
            case MAIN:        drawMainMenu(g2);   break;
            case NAME_INPUT:  drawNameInput(g2);  break;
            case IP_INPUT:    drawIpInput(g2);    break;
            case TEAM_SELECT: drawTeamSelect(g2); break;
        }
    }

    // ── Sub-state renderers ───────────────────────────────────────────────────

    private void drawMainMenu(Graphics2D g2) {
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cx = gp.screenWidth / 2;
        int cy = gp.screenHeight / 2;

        g2.setFont(TITLE_FONT);
        g2.setColor(Color.YELLOW);
        drawCentered(g2, "BATTLE CITY", cx, cy - 80);

        g2.setFont(CTRL_FONT);
        g2.setColor(new Color(0, 220, 220));
        drawCentered(g2, "TEAM 1  │  WASD to move  │  ENTER to fire", cx, cy - 10);

        g2.setColor(new Color(255, 165, 0));
        drawCentered(g2, "TEAM 2  │  Arrows to move  │  SPACE to fire", cx, cy + 20);

        g2.setFont(PROMPT_FONT);
        g2.setColor(Color.WHITE);
        drawCentered(g2, "ENTER / SPACE / L  —  Local Game", cx, cy + 75);

        g2.setColor(new Color(100, 220, 100));
        drawCentered(g2, "H  —  Host Network Game (up to 4 players)", cx, cy + 105);

        g2.setColor(new Color(100, 180, 255));
        drawCentered(g2, "J  —  Join Network Game  (enter server IP)", cx, cy + 130);
    }

    private void drawNameInput(Graphics2D g2) {
        drawInputScreen(g2, "Enter your name:", "(max 12 characters)", inputBuffer.toString());
    }

    private void drawIpInput(Graphics2D g2) {
        drawInputScreen(g2, "Enter server IP:", "(e.g. 192.168.1.5)", inputBuffer.toString());
    }

    private void drawInputScreen(Graphics2D g2, String prompt, String hint, String current) {
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cx = gp.screenWidth / 2;
        int cy = gp.screenHeight / 2;

        g2.setFont(TITLE_FONT);
        g2.setColor(Color.YELLOW);
        drawCentered(g2, "BATTLE CITY", cx, cy - 100);

        g2.setFont(PROMPT_FONT);
        g2.setColor(Color.WHITE);
        drawCentered(g2, prompt, cx, cy - 20);

        // Input box
        int boxW = 320, boxH = 36;
        int boxX = cx - boxW / 2;
        int boxY = cy + 5;
        g2.setColor(new Color(40, 40, 40));
        g2.fillRect(boxX, boxY, boxW, boxH);
        g2.setColor(new Color(180, 180, 180));
        g2.drawRect(boxX, boxY, boxW, boxH);

        g2.setFont(INPUT_FONT);
        g2.setColor(Color.WHITE);
        g2.drawString(current + "_", boxX + 8, boxY + 26);

        g2.setFont(CTRL_FONT);
        g2.setColor(new Color(140, 140, 140));
        drawCentered(g2, hint, cx, cy + 62);
        drawCentered(g2, "ESC — back  |  ENTER — confirm", cx, cy + 87);
    }

    private void drawTeamSelect(Graphics2D g2) {
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cx = gp.screenWidth / 2;
        int cy = gp.screenHeight / 2;

        g2.setFont(TITLE_FONT);
        g2.setColor(Color.YELLOW);
        drawCentered(g2, "BATTLE CITY", cx, cy - 100);

        g2.setFont(PROMPT_FONT);
        g2.setColor(Color.WHITE);
        drawCentered(g2, "Select your team:", cx, cy - 20);

        g2.setColor(new Color(220, 60, 60));
        drawCentered(g2, "R  —  Red Team", cx, cy + 22);

        g2.setColor(new Color(80, 120, 220));
        drawCentered(g2, "B  —  Blue Team", cx, cy + 52);

        if (!teamErrorMsg.isEmpty()) {
            g2.setFont(CTRL_FONT);
            g2.setColor(Color.RED);
            drawCentered(g2, teamErrorMsg, cx, cy + 90);
        }

        g2.setFont(CTRL_FONT);
        g2.setColor(new Color(140, 140, 140));
        drawCentered(g2, "ESC — back", cx, teamErrorMsg.isEmpty() ? cy + 90 : cy + 115);
    }

    private void drawWaitingRoom(Graphics2D g2) {
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, gp.screenWidth, gp.screenHeight);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cx = gp.screenWidth / 2;
        int cy = gp.screenHeight / 2;

        g2.setFont(TITLE_FONT);
        g2.setColor(Color.YELLOW);
        drawCentered(g2, "BATTLE CITY", cx, cy - 100);

        g2.setFont(PROMPT_FONT);
        g2.setColor(Color.WHITE);
        drawCentered(g2, "Waiting for players...", cx, cy - 30);

        g2.setColor(new Color(0, 220, 0));
        String ipLine;
        if (gp.isHost) {
            ipLine = "Your IP: " + NetworkUtils.getLanIp() + "  Port: 5000";
        } else {
            String hostIp = gp.client.getLobbyHostIp();
            ipLine = "Connected to: " + (hostIp.isEmpty() ? "..." : hostIp + ":5000");
        }
        drawCentered(g2, ipLine, cx, cy + 5);

        g2.setColor(Color.WHITE);
        drawCentered(g2, "Players connected: " + gp.client.getLobbyConnected() + " / 4", cx, cy + 40);

        if (gp.isHost) {
            g2.setColor(new Color(200, 220, 100));
            drawCentered(g2, "Press ENTER to start (min 2 players)", cx, cy + 80);
        } else {
            g2.setColor(new Color(160, 160, 160));
            drawCentered(g2, "Waiting for host to start...", cx, cy + 80);
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static void drawCentered(Graphics2D g2, String text, int cx, int y) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, cx - fm.stringWidth(text) / 2, y);
    }
}
