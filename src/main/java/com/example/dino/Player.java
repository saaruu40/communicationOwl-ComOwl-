package com.example.dino;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * The dino player.
 * Sits on the ground, jumps when SPACE is pressed, falls back with gravity.
 */
public class Player {

    // Position and size
    double x      = 80;
    double y;           // set to groundY in constructor
    double width  = 38;
    double height = 48;

    // Physics
    double velocityY   = 0;
    final double gravity     = 0.65;
    final double jumpStrength = -14.5;

    boolean isJumping = false;  // prevents double-jump
    double  groundY;            // y when standing on ground

    public Player(double groundY) {
        this.groundY = groundY - height;
        this.y       = this.groundY;
    }

    /** Call on SPACE key — only jumps when on the ground. */
    public void jump() {
        if (!isJumping) {
            velocityY = jumpStrength;
            isJumping = true;
        }
    }

    /** Apply gravity and clamp to ground each frame. */
    public void update() {
        velocityY += gravity;
        y += velocityY;
        if (y >= groundY) {
            y         = groundY;
            velocityY = 0;
            isJumping = false;
        }
    }

    /** Draw as a glowing purple dino rectangle with an eye. */
    public void render(GraphicsContext gc) {
        // Soft glow behind body
        gc.setFill(Color.web("#9b59b6", 0.25));
        gc.fillRoundRect(x - 5, y - 5, width + 10, height + 10, 10, 10);

        // Main body
        gc.setFill(Color.web("#c084fc"));
        gc.fillRoundRect(x, y, width, height, 7, 7);

        // Eye
        gc.setFill(Color.web("#0f0f1a"));
        gc.fillOval(x + width - 13, y + 11, 9, 9);

        // Eye shine
        gc.setFill(Color.WHITE);
        gc.fillOval(x + width - 11, y + 13, 3, 3);

        // Legs (only when on ground)
        gc.setFill(Color.web("#a855f7"));
        if (!isJumping) {
            gc.fillRoundRect(x + 5,  y + height,     11, 9, 3, 3);
            gc.fillRoundRect(x + 21, y + height,     11, 9, 3, 3);
        }
    }

    public double getX()      { return x; }
    public double getY()      { return y; }
    public double getWidth()  { return width; }
    public double getHeight() { return height; }
}
