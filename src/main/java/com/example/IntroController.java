package com.example;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.IOException;
import java.util.Random;

public class IntroController {

    @FXML private StackPane introLayout;
    @FXML private Canvas    canvas;

    private GraphicsContext gc;
    private final Random rnd   = new Random();
    private long         frame = 0;
    private AnimationTimer timer;

    private double W, H;
    private javafx.scene.image.Image owlImage;

    // Sparks
    private static final int SC = 70;
    private double[] spX = new double[SC], spY = new double[SC];
    private double[] spVx = new double[SC], spVy = new double[SC];
    private double[] spLife = new double[SC], spMaxLife = new double[SC];
    private double[] spR = new double[SC], spHue = new double[SC];

    // Smoke
    private static final int SMC = 20;
    private double[] smX = new double[SMC], smY = new double[SMC];
    private double[] smVx = new double[SMC], smVy = new double[SMC];
    private double[] smLife = new double[SMC], smMaxLife = new double[SMC];
    private double[] smR = new double[SMC];

    // Bats
    private static final int BAT_COUNT = 8;
    private double[] batX = new double[BAT_COUNT], batY = new double[BAT_COUNT];
    private double[] batVx = new double[BAT_COUNT], batVy = new double[BAT_COUNT];
    private double[] batPhase = new double[BAT_COUNT], batSize = new double[BAT_COUNT];

    // Letter
    private double letterPhase = -80;

    // Candle flicker
    private final double[] candleOx      = {-0.60, -0.48, 0.48, 0.60};
    private final double[] candleFlicker = new double[4];

    @FXML
    private void initialize() {
        W = canvas.getWidth();
        H = canvas.getHeight();
        gc = canvas.getGraphicsContext2D();

        // Owl image load
        try {
            owlImage = new javafx.scene.image.Image(
                    getClass().getResourceAsStream("/com/example/Image/Owl.png"));
        } catch (Exception e) {
            owlImage = null;
        }

        for (int i = 0; i < 4; i++) candleFlicker[i] = rnd.nextDouble() * Math.PI * 2;
        for (int i = 0; i < SC; i++)  initSpark(i);
        for (int i = 0; i < SMC; i++) initSmoke(i);
        for (int i = 0; i < BAT_COUNT; i++) initBat(i);

        introLayout.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(1200), introLayout);
        fadeIn.setFromValue(0); fadeIn.setToValue(1); fadeIn.play();

        timer = new AnimationTimer() {
            @Override public void handle(long now) {
                frame++;
                draw();
                letterPhase++;
                if (letterPhase > 360) letterPhase = -80;
                if (frame > 480) { stop(); goToSignUp(); }
            }
        };
        timer.start();
    }

    // ── Init helpers ──────────────────────────────────────
    private void initSpark(int i) {
        spX[i] = rnd(0.39, 0.61); spY[i] = rnd(0.68, 0.76);
        spVx[i] = rnd(-0.0007, 0.0007); spVy[i] = rnd(-0.006, -0.002);
        spLife[i] = 0; spMaxLife[i] = rnd(35, 110);
        spR[i] = rnd(0.5, 2.3); spHue[i] = rnd(260, 300); // purple sparks
    }

    private void initSmoke(int i) {
        smX[i] = rnd(0.46, 0.54); smY[i] = rnd(0.10, 0.18);
        smVx[i] = rnd(-0.0004, 0.0004); smVy[i] = rnd(-0.0009, -0.0003);
        smLife[i] = rnd(0, 160); smMaxLife[i] = rnd(120, 210);
        smR[i] = rnd(8, 24);
    }

    private void initBat(int i) {
        batX[i] = rnd(0.05, 0.95) * W;
        batY[i] = rnd(0.05, 0.45) * H;
        batVx[i] = rnd(-1.5, 1.5);
        if (Math.abs(batVx[i]) < 0.3) batVx[i] = 0.5;
        batVy[i] = rnd(-0.5, 0.5);
        batPhase[i] = rnd(0, Math.PI * 2);
        batSize[i] = rnd(8, 18);
    }

    private double rnd(double a, double b) { return a + rnd.nextDouble() * (b - a); }
    private double noise(double x) {
        return Math.sin(x*2.3)*0.5 + Math.sin(x*5.1)*0.3 + Math.sin(x*11.7)*0.2;
    }

    // ── Main draw ─────────────────────────────────────────
    private void draw() {
        gc.clearRect(0, 0, W, H);
        drawBackground();
        drawMoon();
        drawStars();
        drawGothicSkyline();
        drawBats();
        drawWallTexture();
        drawFloor();
        drawFireplace();
        drawFlames();
        drawSparks();
        drawSmoke();
        drawCandles();
        drawOwlImage();
        drawLetter();
    }

    // ── Dark purple/navy sky ──────────────────────────────
    private void drawBackground() {
        LinearGradient bg = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0,    Color.web("#0a0515")),
                new Stop(0.4,  Color.web("#13083a")),
                new Stop(0.75, Color.web("#1a0a2e")),
                new Stop(1,    Color.web("#0d0820")));
        gc.setFill(bg); gc.fillRect(0, 0, W, H);

        // Purple ambient glow from fireplace
        double gi = 0.10 + 0.03 * Math.sin(frame * 0.06);
        RadialGradient glow = new RadialGradient(0, 0, W*0.5, H*0.82, W*0.65, false,
                CycleMethod.NO_CYCLE,
                new Stop(0,    Color.color(0.55, 0.15, 0.85, gi * 1.8)),
                new Stop(0.35, Color.color(0.35, 0.05, 0.65, gi)),
                new Stop(0.7,  Color.color(0.20, 0.02, 0.40, gi * 0.4)),
                new Stop(1,    Color.TRANSPARENT));
        gc.setFill(glow); gc.fillRect(0, 0, W, H);
    }

    // ── Full moon ─────────────────────────────────────────
    private void drawMoon() {
        double mx = W * 0.5, my = H * 0.30, mr = H * 0.22;

        // Outer glow
        RadialGradient moonGlow = new RadialGradient(0, 0, mx, my, mr * 1.6, false,
                CycleMethod.NO_CYCLE,
                new Stop(0,    Color.color(0.65, 0.55, 0.80, 0.18)),
                new Stop(0.5,  Color.color(0.45, 0.35, 0.65, 0.08)),
                new Stop(1,    Color.TRANSPARENT));
        gc.setFill(moonGlow); gc.fillOval(mx - mr*1.6, my - mr*1.6, mr*3.2, mr*3.2);

        // Moon body
        RadialGradient moonBody = new RadialGradient(0, 0, mx - mr*0.2, my - mr*0.2, mr, false,
                CycleMethod.NO_CYCLE,
                new Stop(0,   Color.color(0.72, 0.68, 0.82, 0.90)),
                new Stop(0.5, Color.color(0.55, 0.50, 0.68, 0.85)),
                new Stop(1,   Color.color(0.38, 0.33, 0.50, 0.80)));
        gc.setFill(moonBody); gc.fillOval(mx - mr, my - mr, mr*2, mr*2);

        // Moon craters
        gc.setFill(Color.color(0.30, 0.26, 0.42, 0.25));
        gc.fillOval(mx + mr*0.2, my - mr*0.3, mr*0.25, mr*0.20);
        gc.fillOval(mx - mr*0.5, my + mr*0.1, mr*0.18, mr*0.15);
        gc.fillOval(mx + mr*0.1, my + mr*0.35, mr*0.14, mr*0.12);
        gc.fillOval(mx - mr*0.3, my - mr*0.5, mr*0.10, mr*0.08);
    }

    // ── Stars ─────────────────────────────────────────────
    private void drawStars() {
        rnd.setSeed(42);
        for (int i = 0; i < 120; i++) {
            double sx = rnd.nextDouble() * W;
            double sy = rnd.nextDouble() * H * 0.55;
            double sa = 0.3 + 0.7 * Math.abs(Math.sin(frame * 0.02 + i));
            double sr = rnd.nextDouble() * 1.5 + 0.3;
            gc.setFill(Color.color(0.85, 0.80, 1.0, sa * 0.6));
            gc.fillOval(sx - sr, sy - sr, sr*2, sr*2);
        }
        rnd.setSeed(System.nanoTime());
    }

    // ── Gothic skyline silhouette ─────────────────────────
    private void drawGothicSkyline() {
        gc.setFill(Color.color(0.06, 0.03, 0.12, 1.0));

        // Left side buildings
        drawChurch(gc, W*0.05, H*0.68, W*0.08, H*0.30);
        drawChurch(gc, W*0.16, H*0.72, W*0.06, H*0.22);
        drawTower(gc, W*0.01, H*0.70, W*0.04, H*0.28);

        // Right side buildings
        drawChurch(gc, W*0.82, H*0.70, W*0.08, H*0.28);
        drawChurch(gc, W*0.92, H*0.73, W*0.07, H*0.24);
        drawTower(gc, W*0.96, H*0.68, W*0.04, H*0.30);

        // Dead trees
        drawDeadTree(gc, W*0.26, H*0.78, H*0.18);
        drawDeadTree(gc, W*0.74, H*0.78, H*0.16);

        // Ground / grass line
        gc.setFill(Color.color(0.06, 0.03, 0.12, 1.0));
        gc.fillRect(0, H*0.82, W, H*0.06);

        // Tombstones
        drawTombstone(gc, W*0.08, H*0.82, W*0.025, H*0.04);
        drawTombstone(gc, W*0.13, H*0.82, W*0.020, H*0.035);
        drawTombstone(gc, W*0.86, H*0.82, W*0.025, H*0.04);
        drawTombstone(gc, W*0.91, H*0.82, W*0.020, H*0.035);
    }

    private void drawChurch(GraphicsContext g, double x, double y, double w, double h) {
        // Body
        g.fillRect(x, y, w, h);
        // Pointed spire
        double[] xs = {x + w/2, x, x + w};
        double[] ys = {y - h*0.5, y, y};
        g.fillPolygon(xs, ys, 3);
        // Window
        g.setFill(Color.color(0.35, 0.15, 0.55, 0.4));
        g.fillOval(x + w*0.35, y + h*0.2, w*0.3, h*0.25);
        g.setFill(Color.color(0.06, 0.03, 0.12, 1.0));
    }

    private void drawTower(GraphicsContext g, double x, double y, double w, double h) {
        g.fillRect(x, y, w, h);
        g.fillRect(x - w*0.1, y - h*0.08, w*1.2, h*0.08);
        // Battlements
        for (int i = 0; i < 4; i++)
            g.fillRect(x + i * w*0.28, y - h*0.14, w*0.18, h*0.08);
    }

    private void drawDeadTree(GraphicsContext g, double x, double y, double h) {
        g.setStroke(Color.color(0.08, 0.04, 0.15, 1.0));
        g.setLineWidth(3); g.strokeLine(x, y, x, y - h);
        g.setLineWidth(2);
        g.strokeLine(x, y - h*0.6, x - h*0.15, y - h*0.85);
        g.strokeLine(x, y - h*0.6, x + h*0.12, y - h*0.80);
        g.setLineWidth(1);
        g.strokeLine(x - h*0.15, y - h*0.85, x - h*0.22, y - h*0.95);
        g.strokeLine(x + h*0.12, y - h*0.80, x + h*0.18, y - h*0.92);
        g.setFill(Color.color(0.06, 0.03, 0.12, 1.0));
    }

    private void drawTombstone(GraphicsContext g, double x, double y, double w, double h) {
        g.setFill(Color.color(0.10, 0.06, 0.18, 1.0));
        g.fillRoundRect(x, y, w, h, 4, 4);
        g.fillOval(x, y - h*0.3, w, h*0.4);
    }

    // ── Bats ──────────────────────────────────────────────
    private void drawBats() {
        for (int i = 0; i < BAT_COUNT; i++) {
            batX[i] += batVx[i];
            batY[i] += batVy[i] + Math.sin(frame * 0.05 + batPhase[i]) * 0.4;
            if (batX[i] < -30) batX[i] = W + 10;
            if (batX[i] > W + 30) batX[i] = -10;
            if (batY[i] < 10) batVy[i] = Math.abs(batVy[i]);
            if (batY[i] > H * 0.5) batVy[i] = -Math.abs(batVy[i]);

            double wingFlap = Math.sin(frame * 0.2 + batPhase[i]);
            double s = batSize[i];
            gc.setFill(Color.color(0.06, 0.03, 0.12, 0.9));
            // Body
            gc.fillOval(batX[i] - s*0.25, batY[i] - s*0.2, s*0.5, s*0.4);
            // Wings
            double wingY = batY[i] + wingFlap * s * 0.3;
            gc.beginPath();
            gc.moveTo(batX[i], batY[i]);
            gc.quadraticCurveTo(batX[i] - s, wingY - s*0.4, batX[i] - s*1.5, batY[i] + s*0.1);
            gc.quadraticCurveTo(batX[i] - s*0.8, wingY + s*0.3, batX[i], batY[i]);
            gc.fill();
            gc.beginPath();
            gc.moveTo(batX[i], batY[i]);
            gc.quadraticCurveTo(batX[i] + s, wingY - s*0.4, batX[i] + s*1.5, batY[i] + s*0.1);
            gc.quadraticCurveTo(batX[i] + s*0.8, wingY + s*0.3, batX[i], batY[i]);
            gc.fill();
        }
    }

    // ── Wall texture (inside room area) ──────────────────
    private void drawWallTexture() {
        gc.save();
        gc.setStroke(Color.color(0.25, 0.10, 0.40, 0.15));
        gc.setLineWidth(0.8);
        double rowH = H * 0.065;
        for (int row = 0; row * rowH < H * 0.90; row++) {
            double y0 = row * rowH;
            gc.strokeLine(0, y0, W, y0);
            double shift = (row % 2) * W * 0.14;
            for (int col = 0; col < 10; col++) {
                double x = shift + col * W * 0.27;
                gc.strokeLine(x, y0, x, y0 + rowH);
            }
        }
        gc.restore();
    }

    // ── Floor ─────────────────────────────────────────────
    private void drawFloor() {
        double fy = H * 0.88;
        LinearGradient fg = new LinearGradient(0, fy, 0, H, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0d0820")),
                new Stop(1, Color.web("#06030f")));
        gc.setFill(fg); gc.fillRect(0, fy, W, H - fy);
        RadialGradient ref = new RadialGradient(0, 0, W*0.5, H*0.93, W*0.32, false,
                CycleMethod.NO_CYCLE,
                new Stop(0,   Color.color(0.50, 0.10, 0.80, 0.12)),
                new Stop(0.6, Color.color(0.30, 0.05, 0.55, 0.05)),
                new Stop(1,   Color.TRANSPARENT));
        gc.setFill(ref); gc.fillRect(0, fy, W, H - fy);
    }

    // ── Fireplace (purple-tinted stone) ───────────────────
    private void drawFireplace() {
        double cx = W*0.5, baseY = H*0.88;
        double fpW = W*0.32, fpH = H*0.43;
        double archTop = baseY - fpH, archW = fpW*0.68;
        double frameW = fpW*1.22, frameH = fpH + H*0.048;
        double mantleY = archTop - H*0.042;

        // Stone frame — dark purple
        gc.setFill(Color.web("#150d28"));
        gc.setStroke(Color.web("#7B2CFF")); gc.setLineWidth(1.5);
        gc.fillRoundRect(cx - frameW/2, mantleY + H*0.042, frameW, frameH, 7, 7);
        gc.strokeRoundRect(cx - frameW/2, mantleY + H*0.042, frameW, frameH, 7, 7);

        // Purple glow on stone
        double si = 0.10 + 0.03 * Math.sin(frame * 0.07);
        RadialGradient sg = new RadialGradient(0, 0, cx, baseY - fpH*0.5, fpW*0.8, false,
                CycleMethod.NO_CYCLE,
                new Stop(0,   Color.color(0.55, 0.15, 1.0, si)),
                new Stop(0.6, Color.color(0.35, 0.05, 0.70, 0.03)),
                new Stop(1,   Color.TRANSPARENT));
        gc.setFill(sg);
        gc.fillRoundRect(cx - frameW/2, mantleY + H*0.042, frameW, frameH, 7, 7);

        // Mantle shelf
        double mantleW = fpW*1.38, mantleH = H*0.042;
        gc.setFill(Color.web("#1a0d35"));
        gc.setStroke(Color.web("#7B2CFF")); gc.setLineWidth(1.2);
        gc.fillRoundRect(cx - mantleW/2, mantleY, mantleW, mantleH, 3, 3);
        gc.strokeRoundRect(cx - mantleW/2, mantleY, mantleW, mantleH, 3, 3);

        // Dark arch opening
        gc.setFill(Color.web("#040209"));
        gc.beginPath();
        gc.moveTo(cx - archW/2, baseY);
        gc.lineTo(cx - archW/2, archTop + fpH*0.75*0.38);
        gc.quadraticCurveTo(cx - archW/2, archTop, cx, archTop);
        gc.quadraticCurveTo(cx + archW/2, archTop, cx + archW/2, archTop + fpH*0.75*0.38);
        gc.lineTo(cx + archW/2, baseY);
        gc.closePath(); gc.fill();

        // Inner arch purple glow
        double ag = 0.20 + 0.06 * Math.sin(frame * 0.07);
        LinearGradient archGlow = new LinearGradient(cx, baseY, cx, archTop, false,
                CycleMethod.NO_CYCLE,
                new Stop(0,   Color.color(0.55, 0.10, 0.90, ag)),
                new Stop(0.4, Color.color(0.35, 0.05, 0.65, 0.10)),
                new Stop(1,   Color.TRANSPARENT));
        gc.setFill(archGlow);
        gc.beginPath();
        gc.moveTo(cx - archW/2, baseY);
        gc.lineTo(cx - archW/2, archTop + fpH*0.75*0.38);
        gc.quadraticCurveTo(cx - archW/2, archTop, cx, archTop);
        gc.quadraticCurveTo(cx + archW/2, archTop, cx + archW/2, archTop + fpH*0.75*0.38);
        gc.lineTo(cx + archW/2, baseY);
        gc.closePath(); gc.fill();

        // Hearth
        gc.setFill(Color.web("#100820"));
        gc.fillRoundRect(cx - fpW*1.08/2, baseY, fpW*1.08, H*0.036, 3, 3);

        // Ash glow — purple
        RadialGradient ashG = new RadialGradient(0, 0, cx, baseY - H*0.01, archW*0.38, false,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(0.55, 0.10, 0.90, 0.20)),
                new Stop(1, Color.TRANSPARENT));
        gc.setFill(ashG);
        gc.fillRect(cx - archW/2, baseY - H*0.04, archW, H*0.04);
    }

    // ── Flames (purple/violet) ────────────────────────────
    private void drawFlames() {
        double cx = W*0.5, baseY = H*0.845;
        double archW = W*0.32*0.68, flameH = H*0.30;

        for (int layer = 0; layer < 4; layer++) {
            double spread = archW * (0.48 - layer*0.09);
            double height = flameH * (1 - layer*0.16);
            double spd    = frame * (0.038 + layer*0.012);
            double alpha  = 0.50 + layer*0.14;

            int steps = 22;
            double[] px = new double[steps + 3];
            double[] py = new double[steps + 3];
            px[0] = cx - spread; py[0] = baseY;
            for (int i = 0; i <= steps; i++) {
                double t = (double)i / steps;
                double bx = (t - 0.5) * spread * 2;
                double wobble = noise(t*4 + spd) * spread * (0.3 - t*0.25) * 0.9;
                double h = height * Math.sin(t*Math.PI) * (0.85 + 0.15*noise(t*7 + spd*1.3));
                px[i+1] = cx + bx + wobble; py[i+1] = baseY - h;
            }
            px[steps+2] = cx + spread; py[steps+2] = baseY;

            Color c0, c1, c2, c3;
            if (layer == 0) {
                c0 = Color.color(0.85, 0.30, 1.0, alpha);
                c1 = Color.color(0.60, 0.10, 0.90, alpha*0.85);
                c2 = Color.color(0.40, 0.05, 0.70, alpha*0.42);
                c3 = Color.color(0.20, 0.02, 0.50, 0);
            } else if (layer == 1) {
                c0 = Color.color(1.0, 0.70, 1.0, alpha*0.9);
                c1 = Color.color(0.80, 0.30, 1.0, alpha*0.82);
                c2 = Color.color(0.55, 0.10, 0.85, alpha*0.40);
                c3 = Color.color(0.30, 0.02, 0.60, 0);
            } else if (layer == 2) {
                c0 = Color.color(1.0, 0.90, 1.0, alpha*0.72);
                c1 = Color.color(0.90, 0.65, 1.0, alpha*0.62);
                c2 = Color.color(0.70, 0.25, 1.0, alpha*0.32);
                c3 = Color.color(0.50, 0.05, 0.90, 0);
            } else {
                c0 = Color.color(1.0, 1.0, 1.0, alpha*0.40);
                c1 = Color.color(0.95, 0.85, 1.0, alpha*0.35);
                c2 = Color.color(0.80, 0.55, 1.0, alpha*0.18);
                c3 = Color.color(0.60, 0.20, 1.0, 0);
            }

            LinearGradient fg = new LinearGradient(cx, baseY, cx, baseY - height, false,
                    CycleMethod.NO_CYCLE,
                    new Stop(0, c0), new Stop(0.35, c1), new Stop(0.72, c2), new Stop(1, c3));
            gc.setFill(fg);
            gc.beginPath(); gc.moveTo(px[0], py[0]);
            for (int i = 1; i < px.length; i++) gc.lineTo(px[i], py[i]);
            gc.closePath(); gc.fill();
        }

        // Bright core — white-purple
        RadialGradient core = new RadialGradient(0, 0, cx, baseY - H*0.038, archW*0.3, false,
                CycleMethod.NO_CYCLE,
                new Stop(0,    Color.color(1.0, 0.95, 1.0, 0.96)),
                new Stop(0.18, Color.color(0.90, 0.70, 1.0, 0.65)),
                new Stop(0.55, Color.color(0.70, 0.20, 1.0, 0.22)),
                new Stop(1,    Color.TRANSPARENT));
        gc.setFill(core);
        gc.fillOval(cx - archW*0.28, baseY - H*0.038 - H*0.082/2, archW*0.56, H*0.082);
    }

    // ── Sparks (purple) ───────────────────────────────────
    private void drawSparks() {
        for (int i = 0; i < SC; i++) {
            spLife[i]++;
            if (spLife[i] > spMaxLife[i]) { initSpark(i); continue; }
            double t = spLife[i] / spMaxLife[i];
            double a = t < 0.1 ? t/0.1 : t > 0.7 ? (1-t)/0.3 : 1;
            gc.setFill(Color.hsb(spHue[i], 0.8, 1.0, a * 0.90));
            double r = spR[i] * (1 - t*0.5);
            gc.fillOval(spX[i]*W - r, spY[i]*H - r, r*2, r*2);
            spX[i] += spVx[i] + Math.sin(spLife[i]*0.16) * 0.00035;
            spY[i] += spVy[i];
            if (spY[i]*H < H*0.04 || spX[i] < 0.28 || spX[i] > 0.72) initSpark(i);
        }
    }

    // ── Smoke ─────────────────────────────────────────────
    private void drawSmoke() {
        for (int i = 0; i < SMC; i++) {
            smLife[i]++;
            if (smLife[i] > smMaxLife[i]) { initSmoke(i); continue; }
            double t = smLife[i] / smMaxLife[i];
            double a = t < 0.1 ? t/0.1 : t > 0.6 ? (1-t)/0.4 : 0.12;
            double r = smR[i] * (1 + t*1.6);
            RadialGradient sg = new RadialGradient(0, 0, smX[i]*W, smY[i]*H, r, false,
                    CycleMethod.NO_CYCLE,
                    new Stop(0, Color.color(0.30, 0.15, 0.45, a)),
                    new Stop(1, Color.TRANSPARENT));
            gc.setFill(sg);
            gc.fillOval(smX[i]*W - r, smY[i]*H - r, r*2, r*2);
            smX[i] += smVx[i] + Math.sin(smLife[i]*0.055)*0.00022;
            smY[i] += smVy[i];
        }
    }

    // ── Candles ───────────────────────────────────────────
    private void drawCandles() {
        double cx = W*0.5, fpW = W*0.32, mantleW = fpW*1.38;
        double fpH = H*0.43, archTop = H*0.88 - fpH;
        double mantleY = archTop - H*0.042;

        for (int i = 0; i < 4; i++) {
            double x = cx + candleOx[i] * mantleW*0.5;
            double cH = H*0.055, cW = W*0.012;

            // Body — pale purple tint
            LinearGradient cg = new LinearGradient(x-cW, 0, x+cW, 0, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#c8b8d8")),
                    new Stop(0.5, Color.web("#e8d8f0")),
                    new Stop(1, Color.web("#b8a8c8")));
            gc.setFill(cg);
            gc.fillRect(x - cW/2, mantleY - cH, cW, cH);

            // Flame
            double flicker = 0.7 + 0.3*Math.sin(frame*0.18 + candleFlicker[i]);
            double fH = H*0.025*flicker;
            LinearGradient fG = new LinearGradient(x, mantleY - cH, x, mantleY - cH - fH, false,
                    CycleMethod.NO_CYCLE,
                    new Stop(0,   Color.color(0.90, 0.60, 1.0, 0.9*flicker)),
                    new Stop(0.5, Color.color(0.70, 0.20, 1.0, 0.7*flicker)),
                    new Stop(1,   Color.TRANSPARENT));
            gc.setFill(fG);
            gc.fillOval(x - cW*0.7*flicker/2, mantleY - cH - fH, cW*0.7*flicker, fH);

            // Core
            gc.setFill(Color.color(1.0, 0.95, 1.0, 0.95*flicker));
            gc.fillOval(x - cW*0.22, mantleY - cH - cW*0.44, cW*0.44, cW*0.44);
        }
    }

    // ── Owl logo (top left, like SignUp page) ─────────────
    private void drawOwlImage() {
        if (owlImage != null) {
            double alpha = Math.min(1.0, frame / 60.0);
            gc.setGlobalAlpha(alpha);
            double size = 90;
            gc.drawImage(owlImage, 20, 20, size, size);
            gc.setGlobalAlpha(1.0);
        }
    }

    // ── Hogwarts-style letter ─────────────────────────────
    private void drawLetter() {
        double t = letterPhase;
        if (t < 0 || t > 360) return;

        double fpX = W*0.5, fpY = H*0.77;
        double px, py, rot, alpha, scaleF;

        if (t < 85) {
            double ease = 1 - Math.pow(1 - t/85.0, 3);
            px = fpX + Math.sin(ease*Math.PI*1.4)*W*0.022;
            py = fpY - ease*H*0.40;
            rot = (1-ease)*0.38 - 0.19 + Math.sin(ease*Math.PI)*0.07;
            alpha = t/22.0; scaleF = 0.28 + ease*0.72;
        } else if (t < 255) {
            double h = (t-85)/170.0;
            px = W*0.5 + Math.sin(h*Math.PI*2)*W*0.013;
            py = H*0.37 + Math.sin(h*Math.PI*3)*H*0.008;
            rot = Math.sin(h*Math.PI*2)*0.013;
            alpha = 1; scaleF = 1;
        } else {
            double out = (t-255)/105.0;
            double ease = out*out;
            px = W*0.5 + ease*W*0.06;
            py = H*0.37 - ease*H*0.26;
            rot = ease*0.18; alpha = 1-ease; scaleF = 1;
        }
        if (alpha <= 0) return;

        double lw = W*0.31, lh = W*0.215;

        gc.save();
        gc.setGlobalAlpha(alpha);
        gc.translate(px, py);
        gc.rotate(Math.toDegrees(rot));
        gc.scale(scaleF, scaleF);

        // Purple fire aura while emerging
        if (t < 85) {
            double fireA = (1 - t/85.0)*0.7;
            RadialGradient glow = new RadialGradient(0, 0, 0, 0, lw*0.82, false,
                    CycleMethod.NO_CYCLE,
                    new Stop(0,   Color.color(0.70, 0.15, 1.0, fireA)),
                    new Stop(0.4, Color.color(0.50, 0.05, 0.85, fireA*0.6)),
                    new Stop(1,   Color.TRANSPARENT));
            gc.setFill(glow);
            gc.fillOval(-lw*0.82, -lh*0.82, lw*1.64, lh*1.64);
        }

        // Parchment — slightly purple-tinted
        LinearGradient pBase = new LinearGradient(-lw/2, -lh/2, lw/2, lh/2, false,
                CycleMethod.NO_CYCLE,
                new Stop(0,    Color.web("#f0e8f8")),
                new Stop(0.3,  Color.web("#e8d8f0")),
                new Stop(0.65, Color.web("#dcc8e8")),
                new Stop(1,    Color.web("#ceb8d8")));
        gc.setFill(pBase);
        gc.fillRoundRect(-lw/2, -lh/2, lw, lh, 10, 10);

        // Scorched edges — purple
        RadialGradient scorch = new RadialGradient(0, 0, -lw/2, lh/2, lw*0.3, false,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(0.20, 0.05, 0.35, 0.55)),
                new Stop(1, Color.TRANSPARENT));
        gc.setFill(scorch); gc.fillRect(-lw/2, -lh/2, lw, lh);

        // Border
        gc.setStroke(Color.web("#7B2CFF")); gc.setLineWidth(1.5);
        gc.strokeRoundRect(-lw/2, -lh/2, lw, lh, 10, 10);

        // Back flap
        gc.setFill(Color.web("#d4c0e8"));
        gc.beginPath();
        gc.moveTo(-lw/2, lh/2); gc.lineTo(0, lh*0.09); gc.lineTo(lw/2, lh/2);
        gc.closePath(); gc.fill();

        // Top flap
        gc.setFill(Color.web("#e0d0f0"));
        gc.beginPath();
        gc.moveTo(-lw/2, -lh/2); gc.lineTo(0, lh*0.14); gc.lineTo(lw/2, -lh/2);
        gc.closePath(); gc.fill();
        gc.setStroke(Color.color(0.48, 0.15, 0.80, 0.4)); gc.setLineWidth(0.7);
        gc.beginPath();
        gc.moveTo(-lw/2, -lh/2); gc.lineTo(0, lh*0.14); gc.lineTo(lw/2, -lh/2);
        gc.stroke();

        // Address text — dark purple ink
        gc.setFont(Font.font("Georgia", FontPosture.ITALIC, lw*0.048));
        gc.setTextAlign(TextAlignment.CENTER);
        String[] lines = {"PingOwl Messenger,", "A message awaits you,", "Beyond the digital veil,", "Connect. Chat. Fly."};
        double gap = lh*0.090, ty0 = -lh*0.22;
        for (int i = 0; i < lines.length; i++) {
            gc.setFill(Color.color(0.25, 0.05, 0.55, 0.9));
            gc.fillText(lines[i], 1, ty0 + i*gap + 1);
            gc.setFill(i == 0 ? Color.web("#4a1a8a") : Color.web("#6030a0"));
            gc.fillText(lines[i], 0, ty0 + i*gap);
        }

        // Wax seal — purple
        double sr = lw*0.09, sy = lh*0.18;
        RadialGradient sealG = new RadialGradient(0, 0, -sr*0.28, sy - sr*0.28, sr*1.1, false,
                CycleMethod.NO_CYCLE,
                new Stop(0,    Color.web("#9b30ff")),
                new Stop(0.25, Color.web("#7B2CFF")),
                new Stop(0.6,  Color.web("#5510cc")),
                new Stop(1,    Color.web("#350880")));
        gc.setFill(sealG);
        gc.fillOval(-sr, sy - sr, sr*2, sr*2);
        gc.setStroke(Color.color(0.30, 0.05, 0.60, 0.7)); gc.setLineWidth(1.2);
        gc.strokeOval(-sr, sy - sr, sr*2, sr*2);

        // Owl symbol on seal
        gc.setFill(Color.color(1, 1, 1, 0.90));
        gc.setFont(Font.font("Times New Roman", FontWeight.BOLD, sr*1.0));
        gc.fillText("P", 0, sy + sr*0.35);

        gc.restore();
    }

    // ── Go to SignUp ──────────────────────────────────────
    private void goToSignUp() {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(1000), introLayout);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            try {
                Stage stage = (Stage) introLayout.getScene().getWindow();
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/example/SignUp.fxml"));
                loader.setRoot(new AnchorPane());
                stage.setScene(new Scene(loader.load()));
                stage.show();
            } catch (IOException ex) { ex.printStackTrace(); }
        });
        fadeOut.play();
    }
}
