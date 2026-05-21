package entity;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public class Entity {
    public int x, y;
    public int speed;

    public BufferedImage up, down, left, right;
    public String direction;
    public Rectangle solidArea;
    public boolean collisionOn = false;
    public boolean active      = true;
}
