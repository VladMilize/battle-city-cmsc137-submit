package core;

import java.awt.Rectangle;
import java.util.List;
import entity.Entity;

public class CollisionChecker {

    GamePanel gp;

    public CollisionChecker(GamePanel gp) {
        this.gp = gp;
    }

    public void checkTile(Entity entity) {

        int entityLeftWorldX   = entity.x + entity.solidArea.x;
        int entityRightWorldX  = entity.x + entity.solidArea.x + entity.solidArea.width;
        int entityTopWorldY    = entity.y + entity.solidArea.y;
        int entityBottomWorldY = entity.y + entity.solidArea.y + entity.solidArea.height;

        int entityLeftCol   = entityLeftWorldX / gp.tileSize;
        int entityRightCol  = entityRightWorldX / gp.tileSize;
        int entityTopRow    = entityTopWorldY / gp.tileSize;
        int entityBottomRow = entityBottomWorldY / gp.tileSize;

        int tileNum1, tileNum2;

        try {
            switch (entity.direction) {
                case "up":
                    entityTopRow = (entityTopWorldY - entity.speed) / gp.tileSize;
                    if (entityTopRow < 0) return;
                    tileNum1 = gp.tileM.mapTileNum[entityLeftCol][entityTopRow];
                    tileNum2 = gp.tileM.mapTileNum[entityRightCol][entityTopRow];
                    if (gp.tileM.tile[tileNum1].collision || gp.tileM.tile[tileNum2].collision)
                        entity.collisionOn = true;
                    break;
                case "down":
                    entityBottomRow = (entityBottomWorldY + entity.speed) / gp.tileSize;
                    if (entityBottomRow >= gp.maxScreenRow) return;
                    tileNum1 = gp.tileM.mapTileNum[entityLeftCol][entityBottomRow];
                    tileNum2 = gp.tileM.mapTileNum[entityRightCol][entityBottomRow];
                    if (gp.tileM.tile[tileNum1].collision || gp.tileM.tile[tileNum2].collision)
                        entity.collisionOn = true;
                    break;
                case "left":
                    entityLeftCol = (entityLeftWorldX - entity.speed) / gp.tileSize;
                    if (entityLeftCol < 0) return;
                    tileNum1 = gp.tileM.mapTileNum[entityLeftCol][entityTopRow];
                    tileNum2 = gp.tileM.mapTileNum[entityLeftCol][entityBottomRow];
                    if (gp.tileM.tile[tileNum1].collision || gp.tileM.tile[tileNum2].collision)
                        entity.collisionOn = true;
                    break;
                case "right":
                    entityRightCol = (entityRightWorldX + entity.speed) / gp.tileSize;
                    if (entityRightCol >= gp.maxScreenCol) return;
                    tileNum1 = gp.tileM.mapTileNum[entityRightCol][entityTopRow];
                    tileNum2 = gp.tileM.mapTileNum[entityRightCol][entityBottomRow];
                    if (gp.tileM.tile[tileNum1].collision || gp.tileM.tile[tileNum2].collision)
                        entity.collisionOn = true;
                    break;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            entity.collisionOn = true;
        }
    }

    /**
     * Projects `mover` one step forward and checks AABB overlap against every
     * entity in `targets`.  Sets mover.collisionOn = true if any target blocks
     * the path (skips self-comparison by reference).
     */
    public void checkEntity(Entity mover, List<Entity> targets) {
        // World-space solid rect of the mover projected one step forward
        Rectangle next = new Rectangle(
            mover.x + mover.solidArea.x,
            mover.y + mover.solidArea.y,
            mover.solidArea.width,
            mover.solidArea.height
        );
        switch (mover.direction) {
            case "up":    next.y -= mover.speed; break;
            case "down":  next.y += mover.speed; break;
            case "left":  next.x -= mover.speed; break;
            case "right": next.x += mover.speed; break;
        }

        for (Entity target : targets) {
            if (target == mover || !target.active) continue;
            Rectangle targetRect = new Rectangle(
                target.x + target.solidArea.x,
                target.y + target.solidArea.y,
                target.solidArea.width,
                target.solidArea.height
            );
            if (next.intersects(targetRect)) {
                mover.collisionOn = true;
                return;
            }
        }
    }
}
