package com.example.dino;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Central game controller.
 * Owns all state: player, obstacle, score, speed, game phase.
 * Called by GameLoop each frame via update() and render().
 */
public class DinoGame {

    // ── Canvas size ────────────────────────────────────────────────────
    public static final double WIDTH    = 800;
    public static final double HEIGHT   = 300;
    public static final double GROUND_Y = 240;

    // ── Phase ──────────────────────────────────────────────────────────
    private boolean started  = false;  // waiting for first SPACE
    private boolean running  = false;  // active gameplay
    private boolean gameOver = false;

    // ── Stats ──────────────────────────────────────────────────────────
    private int    score          = 0;
    private int    frameCount     = 0;
    private double obstacleSpeed  = 5.0;
    private int    highScore      = 0;  // tracked within the window session

    // ── Entities ───────────────────────────────────────────────────────
    private final Player   player;
    private       Obstacle obstacle;

    // ── Star field (background decoration) ────────────────────────────
    private final double[] starX  = new double[70];
    private final double[] starY  = new double[70];
    private final double[] starA  = new double[70]; // alpha brightness

    public DinoGame() {
        player   = new Player(GROUND_Y);
        obstacle = new Obstacle(GROUND_Y, obstacleSpeed);
        for (int i = 0; i < starX.length; i++) {
            starX[i] = Math.random() * WIDTH;
            starY[i] = Math.random() * (GROUND_Y - 20);
            starA[i] = 0.15 + Math.random() * 0.55;
        }
    }

    // ── Update ─────────────────────────────────────────────────────────

    public void update() {
        if (!running) return;

        player.update();
        obstacle.update();

        // Record frames (scoring happens on jump now)
        frameCount++;

        // Speed ramp
        obstacleSpeed = 5.0 + score * 0.012;
        obstacle.setSpeed(obstacleSpeed);

        // Recycle obstacle
        if (obstacle.isOffScreen()) {
            obstacle = new Obstacle(GROUND_Y, obstacleSpeed);
        }

        // Collision → game over
        if (CollisionUtil.checkCollision(player, obstacle)) {
            running  = false;
            gameOver = true;
            if (score > highScore) highScore = score;
        }
    }

    // ── Render ─────────────────────────────────────────────────────────

    public void render(GraphicsContext gc) {
        // Background
        gc.setFill(Color.web("#0f0f1a"));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        // Stars
        for (int i = 0; i < starX.length; i++) {
            gc.setFill(Color.web("#ffffff", starA[i]));
            gc.fillOval(starX[i], starY[i], 2, 2);
        }

        // Ground glow
        LinearGradient glow = new LinearGradient(
            0, GROUND_Y, 0, GROUND_Y + 8,
            false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#7c3aed", 0.9)),
            new Stop(1, Color.web("#7c3aed", 0.0))
        );
        gc.setFill(glow);
        gc.fillRect(0, GROUND_Y, WIDTH, 8);
        gc.setStroke(Color.web("#7c3aed"));
        gc.setLineWidth(2);
        gc.strokeLine(0, GROUND_Y, WIDTH, GROUND_Y);

        // Game objects
        obstacle.render(gc);
        player.render(gc);

        // HUD — score top-right
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 18));
        gc.setFill(Color.web("#c084fc"));
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText("SCORE: " + score, WIDTH - 18, 32);

        // High score
        gc.setFont(Font.font("Monospace", 12));
        gc.setFill(Color.web("#7c3aed", 0.85));
        gc.fillText("BEST: " + highScore, WIDTH - 18, 50);

        // Speed indicator
        gc.fillText("SPD: " + String.format("%.1f", obstacleSpeed), WIDTH - 18, 66);

        // Overlay screens
        if (!started) {
            drawOverlay(gc, "🦕  DINO RUSH", "Press SPACE to start");
        } else if (gameOver) {
            drawOverlay(gc, "💥  GAME OVER",
                "Score: " + score + "   |   Press SPACE to restart · S to share score");
        }
    }

    /** Semi-transparent panel with title + subtitle. */
    private void drawOverlay(GraphicsContext gc, String title, String sub) {
        gc.setFill(Color.web("#0f0f1a", 0.85));
        gc.fillRoundRect(WIDTH / 2 - 270, HEIGHT / 2 - 65, 540, 130, 20, 20);

        gc.setStroke(Color.web("#7c3aed", 0.9));
        gc.setLineWidth(2);
        gc.strokeRoundRect(WIDTH / 2 - 270, HEIGHT / 2 - 65, 540, 130, 20, 20);

        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 24));
        gc.setFill(Color.web("#e9d5ff"));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(title, WIDTH / 2, HEIGHT / 2 - 10);

        gc.setFont(Font.font("Monospace", 13));
        gc.setFill(Color.web("#a78bfa"));
        gc.fillText(sub, WIDTH / 2, HEIGHT / 2 + 25);
    }

    // ── Player input ────────────────────────────────────────────────────

    /**
     * Called on SPACE:
     *  - Before start → start game
     *  - Playing      → jump
     *  - Game over    → restart
     */
    public boolean onSpacePressed() {
        if (!started) {
            started = true;
            running = true;
            return false;
        } else if (gameOver) {
            restart();
            return false;
        } else {
            if (!player.isJumping) {
                player.jump();
                score += 5;
                return true;
            }
            return false;
        }
    }

    private void restart() {
        score         = 0;
        frameCount    = 0;
        obstacleSpeed = 5.0;
        gameOver      = false;
        running       = true;
        player.y         = player.groundY;
        player.velocityY = 0;
        player.isJumping = false;
        obstacle = new Obstacle(GROUND_Y, obstacleSpeed);
    }

    // ── Getters ─────────────────────────────────────────────────────────
    public boolean isGameOver() { return gameOver; }
    public int     getScore()   { return score; }
    public int     getHighScore() { return highScore; }
}
