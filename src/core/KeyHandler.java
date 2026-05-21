package core;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class KeyHandler implements KeyListener {

    // Tank 1 — WASD + Enter
    public boolean upPressed, downPressed, leftPressed, rightPressed, enterPressed;

    // Tank 2 — arrow keys + Space
    public boolean upArrowPressed, downArrowPressed, leftArrowPressed, rightArrowPressed, spacePressed;

    // Network game-over actions
    public boolean rPressed, mPressed;

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        // Tank 1
        if (code == KeyEvent.VK_W)     upPressed    = true;
        if (code == KeyEvent.VK_S)     downPressed  = true;
        if (code == KeyEvent.VK_A)     leftPressed  = true;
        if (code == KeyEvent.VK_D)     rightPressed = true;
        if (code == KeyEvent.VK_ENTER) enterPressed = true;
        // Tank 2
        if (code == KeyEvent.VK_UP)    upArrowPressed    = true;
        if (code == KeyEvent.VK_DOWN)  downArrowPressed  = true;
        if (code == KeyEvent.VK_LEFT)  leftArrowPressed  = true;
        if (code == KeyEvent.VK_RIGHT) rightArrowPressed = true;
        if (code == KeyEvent.VK_SPACE) spacePressed      = true;
        if (code == KeyEvent.VK_R)     rPressed          = true;
        if (code == KeyEvent.VK_M)     mPressed          = true;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        // Tank 1
        if (code == KeyEvent.VK_W)     upPressed    = false;
        if (code == KeyEvent.VK_S)     downPressed  = false;
        if (code == KeyEvent.VK_A)     leftPressed  = false;
        if (code == KeyEvent.VK_D)     rightPressed = false;
        if (code == KeyEvent.VK_ENTER) enterPressed = false;
        // Tank 2
        if (code == KeyEvent.VK_UP)    upArrowPressed    = false;
        if (code == KeyEvent.VK_DOWN)  downArrowPressed  = false;
        if (code == KeyEvent.VK_LEFT)  leftArrowPressed  = false;
        if (code == KeyEvent.VK_RIGHT) rightArrowPressed = false;
        if (code == KeyEvent.VK_SPACE) spacePressed      = false;
        if (code == KeyEvent.VK_R)     rPressed          = false;
        if (code == KeyEvent.VK_M)     mPressed          = false;
    }

    /** Clears all pressed-key state, e.g. when returning to the main menu. */
    public void resetAll() {
        upPressed = downPressed = leftPressed = rightPressed = enterPressed = false;
        upArrowPressed = downArrowPressed = leftArrowPressed = rightArrowPressed = spacePressed = false;
        rPressed = mPressed = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {}
}
