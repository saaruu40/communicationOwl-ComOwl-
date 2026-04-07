package com.example.dino;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;


public class Obstacle {

    static final double CANVAS_WIDTH = 800;

    double x;
    double y;
    double width  = 22;
    double height;   

    double speed;

    public Obstacle(double groundY, double speed) {
        this.height = 38 + Math.random() * 30;
        this.y      = groundY - this.height;
        this.x      = CANVAS_WIDTH + 60;
        this.speed  = speed;
    }

    public void update() {
        x -= speed;
    }

    public boolean isOffScreen() {
        return x + width < 0;
    }

    
    public void render(GraphicsContext gc) {
        
        gc.setFill(Color.web("#22c55e", 0.2));
        gc.fillRoundRect(x - 5, y - 5, width + 10, height + 10, 5, 5);

    
        gc.setFill(Color.web("#4ade80"));
        gc.fillRoundRect(x, y, width, height, 4, 4);

        
        gc.fillRoundRect(x - 14, y + height * 0.25, 16, 9, 3, 3);
        gc.fillRoundRect(x - 14, y + height * 0.08, 9, height * 0.24, 3, 3);

        
        gc.fillRoundRect(x + width - 2, y + height * 0.35, 16, 9, 3, 3);
        gc.fillRoundRect(x + width + 7,  y + height * 0.18, 9, height * 0.25, 3, 3);
    }

    
    public double getX()      { return x - 6; }
    public double getY()      { return y; }
    public double getWidth()  { return width + 12; }
    public double getHeight() { return height; }

    public void setSpeed(double s) { this.speed = s; }
}
