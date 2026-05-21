package map;

import java.awt.image.BufferedImage;

public class Tile {
    public enum Type { EMPTY, BRICK, STEEL, WATER, GRASS, EAGLE }

    public BufferedImage image;
    public boolean collision    = false;
    public boolean destructible = false;
    public Type    type         = Type.EMPTY;
}
