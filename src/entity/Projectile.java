package entity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import core.GamePanel;

public class Projectile extends Entity {

    GamePanel gp;
    public boolean alive    = false;
    public int     ownerNum = 0;

    public Projectile(GamePanel gp) {
        this.gp   = gp;
        solidArea = new Rectangle(0, 0, 10, 10);
        speed     = 10;
    }

    public void set(int x, int y, String direction, boolean alive, int ownerNum) {
        this.x         = x;
        this.y         = y;
        this.direction = direction;
        this.alive     = alive;
        this.ownerNum  = ownerNum;
    }

    public void update() {
        switch (direction) {
            case "up":    y -= speed; break;
            case "down":  y += speed; break;
            case "left":  x -= speed; break;
            case "right": x += speed; break;
        }

        if (x < 0 || x > gp.screenWidth || y < 0 || y > gp.screenHeight) {
            alive = false;
            return;
        }

        // Check bullet vs enemy tanks
        java.awt.Rectangle bulletRect = new java.awt.Rectangle(
            x + solidArea.x, y + solidArea.y, solidArea.width, solidArea.height);
        for (Entity e : gp.tanks) {
            Player target = (Player) e;
            if (target.playerNum == ownerNum || !target.active) continue;
            java.awt.Rectangle tankRect = new java.awt.Rectangle(
                target.x + target.solidArea.x, target.y + target.solidArea.y,
                target.solidArea.width, target.solidArea.height);
            if (bulletRect.intersects(tankRect)) {
                Player shooter = (gp.player.playerNum == ownerNum) ? gp.player : gp.player2;
                shooter.score += 100;
                target.hit();
                alive = false;
                return;
            }
        }

        collisionOn = false;
        gp.cChecker.checkTile(this);

        // Compute the world position one step ahead of the bullet's leading edge
        int cx, cy;
        switch (direction) {
            case "up":
                cx = x + solidArea.x + solidArea.width / 2;
                cy = y + solidArea.y - speed;
                break;
            case "down":
                cx = x + solidArea.x + solidArea.width / 2;
                cy = y + solidArea.y + solidArea.height + speed;
                break;
            case "left":
                cx = x + solidArea.x - speed;
                cy = y + solidArea.y + solidArea.height / 2;
                break;
            default: // right
                cx = x + solidArea.x + solidArea.width + speed;
                cy = y + solidArea.y + solidArea.height / 2;
                break;
        }

        if (collisionOn) {
            gp.tileM.tryDestroyAt(cx, cy);
            gp.tryDestroyEagleAt(cx, cy);
            alive = false;
        } else {
            // Eagle tiles are passable for tanks but must stop bullets
            int col = cx / gp.tileSize;
            int row = cy / gp.tileSize;
            if (col >= 0 && col < gp.maxScreenCol && row >= 0 && row < gp.maxScreenRow) {
                if (gp.tileM.mapTileNum[col][row] == 2) { // EAGLE tile
                    gp.tryDestroyEagleAt(cx, cy);
                    alive = false;
                }
            }
        }
    }

    public void draw(Graphics2D g2) {
        if (alive) {
            g2.setColor(Color.YELLOW);
            g2.fillOval(x + solidArea.x, y + solidArea.y, solidArea.width, solidArea.height);
        }
    }
}
