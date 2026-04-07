package com.example.dino;


public class CollisionUtil {

    public static boolean checkCollision(Player player, Obstacle obstacle) {
        double inset = 9; // shrink hitbox for fairness

        double px = player.getX()  + inset;
        double py = player.getY()  + inset;
        double pw = player.getWidth()  - inset * 2;
        double ph = player.getHeight() - inset * 2;

        double ox = obstacle.getX()  + inset;
        double oy = obstacle.getY();
        double ow = obstacle.getWidth()  - inset * 2;
        double oh = obstacle.getHeight();

        return px < ox + ow &&
               px + pw > ox &&
               py < oy + oh &&
               py + ph > oy;
    }
}
