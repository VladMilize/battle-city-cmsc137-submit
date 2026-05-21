package entity;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.IOException;
import javax.imageio.ImageIO;
import core.GamePanel;

public class Eagle extends Entity {

    GamePanel gp;
    public int team;

    public Eagle(GamePanel gp, int team, int tileCol, int tileRow) {
        this.gp        = gp;
        this.team      = team;
        this.x         = tileCol * gp.tileSize;
        this.y         = tileRow * gp.tileSize;
        this.solidArea = new Rectangle(0, 0, gp.tileSize, gp.tileSize);
        try {
            up = ImageIO.read(getClass().getResourceAsStream("/tiles/eagle.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void draw(Graphics2D g2) {
        if (active) {
            if (up != null) {
                g2.drawImage(up, x, y, gp.tileSize, gp.tileSize, null);
            }
        } else {
            g2.setColor(Color.BLACK);
            g2.fillRect(x, y, gp.tileSize, gp.tileSize);
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(3));
            g2.drawLine(x + 4,              y + 4,              x + gp.tileSize - 4, y + gp.tileSize - 4);
            g2.drawLine(x + gp.tileSize - 4, y + 4,             x + 4,               y + gp.tileSize - 4);
            g2.setStroke(new BasicStroke(1));
        }
    }
}
