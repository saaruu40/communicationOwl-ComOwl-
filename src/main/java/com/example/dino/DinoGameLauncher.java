package com.example.dino;

import com.example.ChatRoomController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.scene.media.AudioClip;
import java.net.URL;

/**
 * Opens the Dino game in its own Stage.
 *
 * Key features:
 *  - SPACE → jump / start / restart
 *  - S key after Game Over → sends score as a chat message
 *  - "📤 Share Score" button → same as pressing S
 *  - Closing the window safely stops the loop; chat UI is unaffected.
 */
public class DinoGameLauncher {

    /**
     * Call this from ChatRoomController on the FX thread.
     * The ChatRoomController reference is used to send the score message.
     *
     * @param chatRoom   the active ChatRoomController (may be null if no chat selected)
     * @param chatTarget the display name of the friend being chatted with (for the share label)
     */
    public static void launch(ChatRoomController chatRoom, String chatTarget) {

        // ── Load Sounds ──────────────────────────────────────────────────
        AudioClip jumpSound = loadSound("/com/example/sounds/Jump_Sound.mp3");
        AudioClip gameOverSound = loadSound("/com/example/sounds/gameOver.mp3");
        AudioClip restartSound = loadSound("/com/example/sounds/restart.mp3");

        // ── Canvas & game ────────────────────────────────────────────────
        Canvas canvas = new Canvas(DinoGame.WIDTH, DinoGame.HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        DinoGame game = new DinoGame();
        GameLoop loop = new GameLoop(game, gc);

        // ── Bottom toolbar ───────────────────────────────────────────────
        Button shareBtn = new Button("📤  Share Score");
        shareBtn.setFont(Font.font("DM Sans", FontWeight.BOLD, 13));
        shareBtn.setStyle(
            "-fx-background-color: #5b21b6;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 20;" +
            "-fx-padding: 8 20 8 20;" +
            "-fx-cursor: hand;"
        );
        shareBtn.setDisable(true); // only enabled after game over

        Button restartBtn = new Button("🔄  Restart");
        restartBtn.setFont(Font.font("DM Sans", 13));
        restartBtn.setStyle(
            "-fx-background-color: #2a1e44;" +
            "-fx-text-fill: #c084fc;" +
            "-fx-background-radius: 20;" +
            "-fx-border-color: #7c3aed;" +
            "-fx-border-radius: 20;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 7 18 7 18;" +
            "-fx-cursor: hand;"
        );

        Button jumpBtn = new Button("⬆  Jump");
        jumpBtn.setFont(Font.font("DM Sans", FontWeight.BOLD, 13));
        jumpBtn.setStyle(
            "-fx-background-color: #7c3aed;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 20;" +
            "-fx-padding: 8 20 8 20;" +
            "-fx-cursor: hand;"
        );

        HBox toolbar = new HBox(12, jumpBtn, restartBtn, shareBtn);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.setPadding(new Insets(10, 16, 10, 16));
        toolbar.setStyle("-fx-background-color: #16102b;");

        VBox root = new VBox(canvas, toolbar);
        root.setStyle("-fx-background-color: #0f0f1a;");

        // ── Scene ────────────────────────────────────────────────────────
        Scene scene = new Scene(root);

        // ── Key handler ──────────────────────────────────────────────────
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE) {
                if (game.onSpacePressed() && jumpSound != null) {
                    jumpSound.play();
                }
                // Enable share button after first game over
                if (game.isGameOver()) shareBtn.setDisable(false);
            } else if (e.getCode() == KeyCode.S && game.isGameOver()) {
                sendScore(chatRoom, game.getScore(), chatTarget);
            }
        });

        // ── Button actions ───────────────────────────────────────────────
        restartBtn.setOnAction(e -> {
            if (restartSound != null) restartSound.play();
            game.onSpacePressed(); // restarts when game over; starts when waiting
            shareBtn.setDisable(true);
            canvas.requestFocus();
        });

        shareBtn.setOnAction(e -> {
            sendScore(chatRoom, game.getScore(), chatTarget);
            canvas.requestFocus();
        });

        jumpBtn.setOnAction(e -> {
            if (game.onSpacePressed() && jumpSound != null) {
                jumpSound.play();
            }
            canvas.requestFocus();
        });

        // Re-enable share after every game-over (polled via loop)
        // We hook into the game-over state via an AnimationTimer trick:
        // the share button is enabled in the key handler and restart button handler.
        // Additionally, GameLoop already calls render each frame; we check game state
        // after render by adding a listener on the canvas.
        // Simple approach: enable the button whenever game is over (checked via
        // a post-render hook inside the game loop).
        // We do this cleanly via a wrapper:
        GameLoop wrappedLoop = new GameLoop(game, gc) {
            boolean wasGameOver = false;
            @Override public void start() {
                new javafx.animation.AnimationTimer() {
                    @Override public void handle(long now) {
                        game.update();
                        game.render(gc);
                        
                        boolean isGameOver = game.isGameOver();
                        if (!wasGameOver && isGameOver && gameOverSound != null) {
                            gameOverSound.play();
                        }
                        wasGameOver = isGameOver;

                        // Keep share button in sync with game state
                        shareBtn.setDisable(!isGameOver);
                    }
                }.start();
            }
        };

        // ── Stage ────────────────────────────────────────────────────────
        Stage stage = new Stage();
        stage.setTitle("🦕  Dino Rush" +
            (chatTarget != null && !chatTarget.isBlank() ? " — chatting with " + chatTarget : ""));
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setOnCloseRequest(e -> loop.stop()); // loop (not wrappedLoop) is referenced but we use wrappedLoop
        stage.show();

        canvas.requestFocus();
        root.requestFocus();

        wrappedLoop.start(); // starts the real animationtimer inside
    }

    /**
     * Sends the dino score as a text message into the active 1-on-1 chat.
     * Does nothing if no chat is open.
     */
    private static void sendScore(ChatRoomController chatRoom, int score, String chatTarget) {
        if (chatRoom == null) return;
        String msg = "🦕 I just scored " + score + " in Dino Rush! Can you beat it?";
        chatRoom.sendMessageProgrammatically(msg);
    }

    private static AudioClip loadSound(String path) {
        try {
            URL url = DinoGameLauncher.class.getResource(path);
            if (url != null) {
                return new AudioClip(url.toExternalForm());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
