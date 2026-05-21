package map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.imageio.ImageIO;
import core.GamePanel;

public class TileManager {

    GamePanel gp;
    public Tile[] tile;
    public int[][] mapTileNum;

    public TileManager(GamePanel gp) {
        this.gp = gp;

        tile       = new Tile[6];
        mapTileNum = new int[gp.maxScreenCol][gp.maxScreenRow];

        getTileImage();
        loadMap("/maps/map1.txt");
    }

    public void getTileImage() {
        try {
            // 0 — EMPTY
            tile[0]       = new Tile();
            tile[0].image = ImageIO.read(getClass().getResourceAsStream("/tiles/black.png"));
            tile[0].type  = Tile.Type.EMPTY;

            // 1 — BRICK: blocks movement, bullets destroy it
            tile[1]             = new Tile();
            tile[1].image       = ImageIO.read(getClass().getResourceAsStream("/tiles/bricktile.png"));
            tile[1].collision   = true;
            tile[1].destructible = true;
            tile[1].type        = Tile.Type.BRICK;

            // 2 — EAGLE: passable for tanks; bullet-eagle collision handled in Projectile
            tile[2]           = new Tile();
            tile[2].image     = ImageIO.read(getClass().getResourceAsStream("/tiles/eagle.png"));
            tile[2].collision = false;
            tile[2].type      = Tile.Type.EAGLE;
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 3 — STEEL: blocks movement, bullets cannot destroy it
        tile[3]           = new Tile();
        tile[3].image     = makeSolidTile(new Color(0x8C8C8C), new Color(0xC8C8C8));
        tile[3].collision = true;
        tile[3].type      = Tile.Type.STEEL;

        // 4 — WATER: blocks tanks and bullets, indestructible
        tile[4]           = new Tile();
        tile[4].image     = makeSolidTile(new Color(0x1565C0), new Color(0x42A5F5));
        tile[4].collision = true;
        tile[4].type      = Tile.Type.WATER;

        // 5 — GRASS: passable, renders on top of entities
        tile[5]       = new Tile();
        tile[5].image = makeSolidTile(new Color(0x1B5E20), new Color(0x4CAF50));
        tile[5].type  = Tile.Type.GRASS;
    }

    /** Creates a simple two-tone tile image without needing a PNG file. */
    private BufferedImage makeSolidTile(Color base, Color highlight) {
        int s = gp.tileSize;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(base);
        g.fillRect(0, 0, s, s);
        g.setColor(highlight);
        g.fillRect(2, 2, s - 4, s / 3);
        g.dispose();
        return img;
    }

    public void loadMap(String filePath) {
        try {
            InputStream is = getClass().getResourceAsStream(filePath);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));

            int col = 0;
            int row = 0;

            while (col < gp.maxScreenCol && row < gp.maxScreenRow) {
                String line = br.readLine();
                while (col < gp.maxScreenCol) {
                    String[] numbers = line.split(" ");
                    mapTileNum[col][row] = Integer.parseInt(numbers[col]);
                    col++;
                }
                if (col == gp.maxScreenCol) {
                    col = 0;
                    row++;
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Destroys the tile at (worldX, worldY) if it is BRICK. */
    public void tryDestroyAt(int worldX, int worldY) {
        int col = worldX / gp.tileSize;
        int row = worldY / gp.tileSize;
        if (col < 0 || col >= gp.maxScreenCol || row < 0 || row >= gp.maxScreenRow) return;
        int idx = mapTileNum[col][row];
        if (tile[idx].destructible) {
            mapTileNum[col][row] = 0;
        }
    }

    public void draw(Graphics2D g2) {
        int col = 0, row = 0, x = 0, y = 0;

        while (col < gp.maxScreenCol && row < gp.maxScreenRow) {
            int tileNum = mapTileNum[col][row];
            // GRASS and EAGLE are drawn separately (after entities); skip them here
            if (tile[tileNum].type != Tile.Type.GRASS && tile[tileNum].type != Tile.Type.EAGLE) {
                g2.drawImage(tile[tileNum].image, x, y, gp.tileSize, gp.tileSize, null);
            }
            col++;
            x += gp.tileSize;
            if (col == gp.maxScreenCol) {
                col = 0;
                x   = 0;
                row++;
                y  += gp.tileSize;
            }
        }
    }

    /** Second draw pass: renders GRASS tiles on top of entities. */
    public void drawGrassOverlay(Graphics2D g2) {
        int col = 0, row = 0, x = 0, y = 0;
        while (col < gp.maxScreenCol && row < gp.maxScreenRow) {
            int tileNum = mapTileNum[col][row];
            if (tile[tileNum].type == Tile.Type.GRASS) {
                g2.drawImage(tile[tileNum].image, x, y, gp.tileSize, gp.tileSize, null);
            }
            col++;
            x += gp.tileSize;
            if (col == gp.maxScreenCol) {
                col = 0;
                x   = 0;
                row++;
                y  += gp.tileSize;
            }
        }
    }
}
