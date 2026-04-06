package com.example;

import javafx.animation.AnimationTimer;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

import java.net.URL;
import java.util.Random;
import java.util.ResourceBundle;

/**
 * ComOwl — Particle Background Controller
 * Pair with: ComOwlParticles.fxml + comowl-particles.css
 */
public class ComOwlParticleController implements Initializable {

    // ── FXML injections ──────────────────────────────────────────────────────
    @FXML
    private StackPane rootPane;
    @FXML
    private Pane particlePane;

    // ── Canvas size (dynamic) ────────────────────────────────────────────────
    private double W = 1920;
    private double H = 1080;

    // ── Particle tuning ──────────────────────────────────────────────────────
    private static final int COUNT = 100; // 80–120
    private static final double REPULSE_RADIUS = 120.0;
    private static final double REPULSE_FORCE = 4.2;
    private static final double DAMPING = 0.87; // 0.95 = floaty · 0.80 = snappy
    private static final double MAX_SPEED = 6.0;
    private static final double WANDER = 0.06;

    // ── Particle state — primitive arrays (zero GC per frame) ────────────────
    private final double[] px = new double[COUNT]; // current X
    private final double[] py = new double[COUNT]; // current Y
    private final double[] vx = new double[COUNT]; // velocity X
    private final double[] vy = new double[COUNT]; // velocity Y
    private final double[] bx = new double[COUNT]; // home X (wander target)
    private final double[] by = new double[COUNT]; // home Y
    private final double[] rad = new double[COUNT]; // visual radius

    private final Circle[] dots = new Circle[COUNT];
    private final Random rng = new Random();

    // ── Mouse position ────────────────────────────────────────────────────────
    private double mouseX = -9999;
    private double mouseY = -9999;

    // ── CSS style classes (defined in comowl-particles.css) ───────────────────
    private static final String[] DOT_STYLES = {
            "dot-violet",
            "dot-lavender",
            "dot-indigo",
            "dot-lilac",
            "dot-soft"
    };

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        spawnParticles();
        startLoop();
    }

    @FXML
    private void handleGetStarted(ActionEvent event) {
        try {
            App.setRoot("SignIn");
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Call this from App.java right after loader.load() and scene creation.
     * Mouse events need a Scene reference — not available during initialize().
     *
     * FXMLLoader loader = new FXMLLoader(...);
     * Parent root = loader.load();
     * Scene scene = new Scene(root, 900, 620);
     * ComOwlParticleController ctrl = loader.getController();
     * ctrl.bindScene(scene); // ← wire mouse here
     */
    public void bindScene(Scene scene) {
        scene.setOnMouseMoved(e -> {
            mouseX = e.getX();
            mouseY = e.getY();
        });
        scene.setOnMouseDragged(e -> {
            mouseX = e.getX();
            mouseY = e.getY();
        });
        scene.setOnMouseExited(e -> {
            mouseX = -9999;
            mouseY = -9999;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spawn all Circle nodes into particlePane
    // ─────────────────────────────────────────────────────────────────────────
    private void spawnParticles() {
        for (int i = 0; i < COUNT; i++) {

            px[i] = rng.nextDouble() * W;
            py[i] = rng.nextDouble() * H;
            bx[i] = px[i];
            by[i] = py[i];

            vx[i] = (rng.nextDouble() - 0.5) * 0.8;
            vy[i] = (rng.nextDouble() - 0.5) * 0.8;

            rad[i] = 1.5 + rng.nextDouble() * 2.5;

            Circle dot = new Circle(rad[i]);
            dot.setCenterX(px[i]);
            dot.setCenterY(py[i]);
            dot.setMouseTransparent(true);
            dot.setOpacity(0.4 + rng.nextDouble() * 0.6);

            // Assign random glow class from CSS
            dot.getStyleClass().add(DOT_STYLES[rng.nextInt(DOT_STYLES.length)]);

            dots[i] = dot;
            particlePane.getChildren().add(dot);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AnimationTimer — handle() fires every JavaFX pulse (~60 FPS)
    // ─────────────────────────────────────────────────────────────────────────
    private void startLoop() {
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick();
            }
        }.start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Per-frame physics
    // ─────────────────────────────────────────────────────────────────────────
    private void tick() {
        double currentW = particlePane.getWidth();
        double currentH = particlePane.getHeight();
        if (currentW > 0 && currentH > 0) {
            if (currentW != W || currentH != H) {
                double scaleX = currentW / W;
                double scaleY = currentH / H;
                for (int i = 0; i < COUNT; i++) {
                    bx[i] *= scaleX;
                    by[i] *= scaleY;
                    px[i] *= scaleX;
                    py[i] *= scaleY;
                }
                W = currentW;
                H = currentH;
            }
        }

        for (int i = 0; i < COUNT; i++) {

            // A — Wander: soft pull home + organic jitter
            double ax = (bx[i] - px[i]) * 0.0011
                    + (rng.nextDouble() - 0.5) * WANDER;
            double ay = (by[i] - py[i]) * 0.0011
                    + (rng.nextDouble() - 0.5) * WANDER;

            // B — Mouse repulsion
            double dx = px[i] - mouseX;
            double dy = py[i] - mouseY;
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist < REPULSE_RADIUS && dist > 0.5) {
                double force = (1.0 - dist / REPULSE_RADIUS) * REPULSE_FORCE;
                double nx = dx / dist;
                double ny = dy / dist;
                ax += nx * force;
                ay += ny * force;
                bx[i] += nx * force * 0.06; // home drifts — "scared" effect
                by[i] += ny * force * 0.06;
            }

            // C — Integrate velocity
            vx[i] += ax;
            vy[i] += ay;

            // D — Speed cap
            double speed = Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i]);
            if (speed > MAX_SPEED) {
                double inv = MAX_SPEED / speed;
                vx[i] *= inv;
                vy[i] *= inv;
            }

            // E — Integrate position
            px[i] += vx[i];
            py[i] += vy[i];

            // F — Velocity damping (exponential ease-out)
            vx[i] *= DAMPING;
            vy[i] *= DAMPING;

            // G — Edge wrap
            if (px[i] < -10) {
                px[i] = W + 10;
                bx[i] = px[i];
            }
            if (px[i] > W + 10) {
                px[i] = -10;
                bx[i] = px[i];
            }
            if (py[i] < -10) {
                py[i] = H + 10;
                by[i] = py[i];
            }
            if (py[i] > H + 10) {
                py[i] = -10;
                by[i] = py[i];
            }

            // H — Update Circle node
            dots[i].setCenterX(px[i]);
            dots[i].setCenterY(py[i]);
        }
    }
}