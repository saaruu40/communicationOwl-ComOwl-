package com.example.dino;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;


public class Player {

    
    double x      = 80;
    double y;           
    double width  = 38;
    double height = 48;

    
    double velocityY   = 0;
    final double gravity     = 0.65;
    final double jumpStrength = -14.5;

    boolean isJumping = false;  
    double  groundY;            

    public Player(double groundY) {
        this.groundY = groundY - height;
        this.y       = this.groundY;
    }

    
    public void jump() {
        if (!isJumping) {
            velocityY = jumpStrength;
            isJumping = true;
        }
    }

   
    public void update() {
        velocityY += gravity;
        y += velocityY;
        if (y >= groundY) {
            y         = groundY;
            velocityY = 0;
            isJumping = false;
        }
    }

    
    public void render(GraphicsContext gc) {
        
        gc.setFill(Color.web("#9b59b6", 0.25));
        gc.fillRoundRect(x - 5, y - 5, width + 10, height + 10, 10, 10);

        
        gc.setFill(Color.web("#c084fc"));
        gc.fillRoundRect(x, y, width, height, 7, 7);

        
        gc.setFill(Color.web("#0f0f1a"));
        gc.fillOval(x + width - 13, y + 11, 9, 9);

        
        gc.setFill(Color.WHITE);
        gc.fillOval(x + width - 11, y + 13, 3, 3);

        
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
