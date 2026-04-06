package com.example.dino;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.GraphicsContext;

/**
 * Wraps JavaFX AnimationTimer to drive the game loop.
 * Calls update() then render() on every display frame (~60 fps).
 */
public class GameLoop {

    private final AnimationTimer timer;

    public GameLoop(DinoGame game, GraphicsContext gc) {
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                game.update();
                game.render(gc);
            }
        };
    }

    public void start() { timer.start(); }
    public void stop()  { timer.stop();  }
}
