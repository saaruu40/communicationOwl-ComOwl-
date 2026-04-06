package com.example;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class ImageCropperController {

    @FXML
    private Canvas imageCanvas;
    @FXML
    private Canvas overlayCanvas;
    @FXML
    private Button cropButton;
    @FXML
    private Button cancelButton;
    @FXML
    private StackPane canvasPane;

    private Image originalImage;
    private double imageX, imageY; // Image position
    private double imageDrawWidth; // Drawn width of the image
    private double imageDrawHeight; // Drawn height of the image
    private double dragStartX, dragStartY; // Starting point of drag

    private static final double CANVAS_SIZE = 400;
    private static final double CIRCLE_SIZE = 300; // Circular crop area
    private static final double CIRCLE_X = (CANVAS_SIZE - CIRCLE_SIZE) / 2;
    private static final double CIRCLE_Y = (CANVAS_SIZE - CIRCLE_SIZE) / 2;

    // Will send the result back to SignUpController
    public interface CropCallback {
        void onCropped(WritableImage croppedImage, String base64);
    }

    private CropCallback cropCallback;

    public void setCropCallback(CropCallback callback) {
        this.cropCallback = callback;
    }

    public void setImage(Image image) {
        this.originalImage = image;

        // Fit the image into the Canvas
        double ratio = Math.max(
                CANVAS_SIZE / image.getWidth(),
                CANVAS_SIZE / image.getHeight());
        imageDrawWidth = image.getWidth() * ratio;
        imageDrawHeight = image.getHeight() * ratio;

        // Keep it centered
        imageX = (CANVAS_SIZE - imageDrawWidth) / 2;
        imageY = (CANVAS_SIZE - imageDrawHeight) / 2;

        drawAll();
    }

    @FXML
    private void initialize() {
        // Move the image using mouse drag
        imageCanvas.setOnMousePressed(e -> {
            dragStartX = e.getX() - imageX;
            dragStartY = e.getY() - imageY;
        });

        imageCanvas.setOnMouseDragged(e -> {
            imageX = e.getX() - dragStartX;
            imageY = e.getY() - dragStartY;
            drawAll();
        });

        overlayCanvas.setOnMousePressed(e -> {
            dragStartX = e.getX() - imageX;
            dragStartY = e.getY() - imageY;
        });

        overlayCanvas.setOnMouseDragged(e -> {
            imageX = e.getX() - dragStartX;
            imageY = e.getY() - dragStartY;
            drawAll();
        });
    }

    private void drawAll() {
        drawImage();
        drawOverlay();
    }

    private void drawImage() {
        GraphicsContext gc = imageCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
        gc.drawImage(originalImage, imageX, imageY, imageDrawWidth, imageDrawHeight);
    }

    private void drawOverlay() {
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);

        // Dark overlay
        gc.setFill(Color.color(0, 0, 0, 0.6));
        gc.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);

        // Clear the circular area
        gc.clearRect(CIRCLE_X, CIRCLE_Y, CIRCLE_SIZE, CIRCLE_SIZE);

        // Circular border
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeOval(CIRCLE_X, CIRCLE_Y, CIRCLE_SIZE, CIRCLE_SIZE);
    }

    @FXML
    private void cropImage() {
        // Crop the image from the circular area
        double scaleX = originalImage.getWidth() / imageDrawWidth;
        double scaleY = originalImage.getHeight() / imageDrawHeight;

        double srcX = (CIRCLE_X - imageX) * scaleX;
        double srcY = (CIRCLE_Y - imageY) * scaleY;
        double srcSize = CIRCLE_SIZE * scaleX;

        // Check bounds
        srcX = Math.max(0, srcX);
        srcY = Math.max(0, srcY);
        if (srcX + srcSize > originalImage.getWidth())
            srcSize = originalImage.getWidth() - srcX;
        if (srcY + srcSize > originalImage.getHeight())
            srcSize = originalImage.getHeight() - srcY;

        // Crop
        PixelReader reader = originalImage.getPixelReader();
        WritableImage cropped = new WritableImage(reader,
                (int) srcX, (int) srcY, (int) srcSize, (int) srcSize);

        // Convert to Base64
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try {
            java.awt.image.BufferedImage buffered = new java.awt.image.BufferedImage(
                    (int) cropped.getWidth(),
                    (int) cropped.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);

            javafx.embed.swing.SwingFXUtils.fromFXImage(cropped, buffered);

            javax.imageio.ImageIO.write(buffered, "png", bos);
            String base64 = java.util.Base64.getEncoder().encodeToString(bos.toByteArray());

            if (cropCallback != null) {
                cropCallback.onCropped(cropped, base64);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Close the window
        ((Stage) cropButton.getScene().getWindow()).close();
    }

    @FXML
    private void cancelCrop() {
        ((Stage) cancelButton.getScene().getWindow()).close();
    }
}