package entity;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import core.GamePanel;
import core.KeyHandler;

public class Player extends Entity {

    GamePanel  gp;
    KeyHandler keyH;
    public int playerNum;
    int        shotAvailableCounter = 0;

    public int  lives        = 3;
    public int  score        = 0;
    private int respawnTimer = 0;
    private static final int RESPAWN_TICKS = 120;

    public Player(GamePanel gp, KeyHandler keyH, int playerNum) {
        this.gp        = gp;
        this.keyH      = keyH;
        this.playerNum = playerNum;

        solidArea = new Rectangle(0, 0, gp.tileSize, gp.tileSize);

        setDefaultValues();
        getPlayerImage();
    }

    public void setDefaultValues() {
        speed                = 4;
        direction            = "up";
        active               = true;
        shotAvailableCounter = 30;
        if (playerNum == 1) {
            x = gp.tileSize * 4;
            y = gp.tileSize * 10;
        } else {
            x         = gp.tileSize * 11;
            y         = gp.tileSize * 1;
            direction = "down";
        }
    }

    public void getPlayerImage() {
        try {
            if (playerNum == 1) {
                up    = ImageIO.read(getClass().getResourceAsStream("/player/up2.png"));
                down  = ImageIO.read(getClass().getResourceAsStream("/player/down2.png"));
                left  = ImageIO.read(getClass().getResourceAsStream("/player/left2.png"));
                right = ImageIO.read(getClass().getResourceAsStream("/player/right2.png"));
            } else {
                up    = ImageIO.read(getClass().getResourceAsStream("/player/up.png"));
                down  = ImageIO.read(getClass().getResourceAsStream("/player/down.png"));
                left  = ImageIO.read(getClass().getResourceAsStream("/player/left.png"));
                right = ImageIO.read(getClass().getResourceAsStream("/player/right.png"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void hit() {
        active = false;
        lives--;
        respawnTimer = (lives > 0) ? RESPAWN_TICKS : 0;
    }

    public void update() {
        if (!active) {
            if (respawnTimer > 0) {
                respawnTimer--;
            } else if (lives > 0) {
                setDefaultValues();
            }
            return;
        }

        boolean movingUp    = (playerNum == 1) ? keyH.upPressed    : keyH.upArrowPressed;
        boolean movingDown  = (playerNum == 1) ? keyH.downPressed  : keyH.downArrowPressed;
        boolean movingLeft  = (playerNum == 1) ? keyH.leftPressed  : keyH.leftArrowPressed;
        boolean movingRight = (playerNum == 1) ? keyH.rightPressed : keyH.rightArrowPressed;
        boolean firing      = (playerNum == 1) ? keyH.enterPressed : keyH.spacePressed;

        if (movingUp || movingDown || movingLeft || movingRight) {

            if      (movingUp)    direction = "up";
            else if (movingDown)  direction = "down";
            else if (movingLeft)  direction = "left";
            else if (movingRight) direction = "right";

            collisionOn = false;
            gp.cChecker.checkTile(this);
            gp.cChecker.checkEntity(this, gp.tanks);

            if (!collisionOn) {
                switch (direction) {
                    case "up":    y -= speed; break;
                    case "down":  y += speed; break;
                    case "left":  x -= speed; break;
                    case "right": x += speed; break;
                }
            }
        }

        if (firing && shotAvailableCounter == 30) {
            int half = gp.tileSize / 2 - 5; // center of tile minus half bullet width
            int bx, by;
            switch (direction) {
                case "up":    bx = x + half;             by = y;                      break;
                case "down":  bx = x + half;             by = y + gp.tileSize;        break;
                case "left":  bx = x;                    by = y + half;               break;
                default:      bx = x + gp.tileSize;      by = y + half;               break;
            }
            Projectile p = new Projectile(gp);
            p.set(bx, by, direction, true, playerNum);
            gp.projectileList.add(p);
            shotAvailableCounter = 0;
        }

        if (shotAvailableCounter < 30) {
            shotAvailableCounter++;
        }
    }

    public void draw(Graphics2D g2) {
        if (!active) return;

        Color borderColor = (playerNum == 1) ? new Color(220, 60, 60) : new Color(60, 100, 220);

        BufferedImage image = null;
        switch (direction) {
            case "up":    image = up;    break;
            case "down":  image = down;  break;
            case "left":  image = left;  break;
            case "right": image = right; break;
        }

        if (image != null) {
            // Filled border rect 4 px outside the tank, then sprite on top
            g2.setColor(borderColor);
            g2.fillRect(x - 4, y - 4, gp.tileSize + 8, gp.tileSize + 8);
            g2.drawImage(image, x, y, gp.tileSize, gp.tileSize, null);
        } else {
            // Fallback: neutral body rectangle, then 3 px border outline
            Color body = (playerNum == 1) ? new Color(180, 60, 60) : new Color(60, 80, 180);
            g2.setColor(body);
            g2.fillRect(x, y, gp.tileSize, gp.tileSize);
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(3));
            g2.drawRect(x, y, gp.tileSize - 1, gp.tileSize - 1);
            g2.setStroke(new BasicStroke(1));
        }
    }
}
