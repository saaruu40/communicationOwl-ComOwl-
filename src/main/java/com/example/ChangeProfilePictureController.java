package com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;

public class ChangeProfilePictureController {

    @FXML private ImageView profileImageView;

    public static Popup currentPopup;
    private String newProfilePicBase64 = "";

    private Stage getMainStage() {
        return (Stage) Stage.getWindows().stream()
                .filter(w -> w instanceof Stage && w.isShowing())
                .findFirst().orElse(null);
    }

    @FXML
    private void initialize() {
        if (HomeController.currentProfilePic != null
                && !HomeController.currentProfilePic.isEmpty()) {
            byte[] imageBytes = Base64.getDecoder()
                    .decode(HomeController.currentProfilePic);
            Image image = new Image(new ByteArrayInputStream(imageBytes));
            profileImageView.setImage(image);
        } else {
            try {
                Image defaultImage = new Image(
                        getClass().getResourceAsStream(
                                "/com/example/Image/default_avatar.png"));
                profileImageView.setImage(defaultImage);
            } catch (Exception e) {
                // Use empty if default not found
            }
        }
        profileImageView.setFitWidth(180);
        profileImageView.setFitHeight(180);
        Circle clip = new Circle(90, 90, 90);
        profileImageView.setClip(clip);
    }

    @FXML
    private void chooseImage() {
        // Keep popup visible while file chooser is open (avoids auto-hide closing it)
        Popup popup = currentPopup;
        if (popup != null) {
            popup.setAutoHide(false);
        }

        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Choose Profile Picture");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));

            File file = fileChooser.showOpenDialog(getMainStage());

            if (file != null) {
                try {
                    Image image = new Image(file.toURI().toString());
                    profileImageView.setImage(image);
                    Circle clip = new Circle(90, 90, 90);
                    profileImageView.setClip(clip);

                    FileInputStream fis = new FileInputStream(file);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fis.read(buf)) != -1) bos.write(buf, 0, bytesRead);
                    newProfilePicBase64 = Base64.getEncoder().encodeToString(bos.toByteArray());
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            if (popup != null) {
                popup.setAutoHide(true);
            }
        }
    }

    private void loadBase64Image(ImageView iv, String base64, int fallbackSize) {
        if (iv == null || base64 == null || base64.isEmpty()) {
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            Image img = new Image(new java.io.ByteArrayInputStream(bytes));
            if (!img.isError()) {
                iv.setImage(img);
                return;
            }
        } catch (Exception ignored) {}

        // Fallback to default avatar
        try {
            Image defaultImg = new Image(getClass().getResourceAsStream("/com/example/Image/default_avatar.png"));
            if (defaultImg != null && !defaultImg.isError()) {
                iv.setImage(defaultImg);
                return;
            }
        } catch (Exception ignored) {}
    }

    @FXML
    private void submitPicture() {
        if (newProfilePicBase64.isEmpty()) return;

        String response = SocketClient.send(
                "UPDATE_PICTURE|" + HomeController.currentEmail + "|" + newProfilePicBase64);
        System.out.println("[ChangeProfilePicture] server response: " + response);

        if (isUpdateSuccessful(response)) {
            HomeController.currentProfilePic = newProfilePicBase64;

            // Refresh from server source-of-truth to avoid local sync drift.
            String profileResp = SocketClient.send("GET_USER_INFO|" + HomeController.currentEmail);
            System.out.println("[ChangeProfilePicture] refresh user info: " + profileResp);
            if (profileResp != null && !profileResp.equals("ERROR") && !profileResp.equals("CONNECTION_ERROR")) {
                String[] parts = profileResp.split("\\|", 3);
                if (parts.length > 2) {
                    HomeController.currentProfilePic = parts[2];
                }
            }

            HomeController homeInstance = HomeController.getInstance();
            if (homeInstance != null) {
                homeInstance.loadProfilePicture();
            }

            // Update this popup preview immediately
            if (!HomeController.currentProfilePic.isEmpty()) {
                loadBase64Image(profileImageView, HomeController.currentProfilePic, 180);
            }

            ChatRoomController chatInstance = ChatRoomController.getInstance();
            if (chatInstance != null) chatInstance.onAccountUpdate();
            goBack();
        } else {
            // Optionally keep old profile picture or show message
            System.out.println("[ChangeProfilePicture] update failed");
        }
    }

    private boolean isUpdateSuccessful(String response) {
        if (response == null) return false;
        String normalized = response.trim().toUpperCase();
        return normalized.contains("SUCCESS")
                || normalized.contains("UPDATED")
                || normalized.contains("OK")
                || normalized.contains("DONE")
                || normalized.contains("UPDATE_SUCCESS");
    }

    @FXML
    private void goBack() {
        try {
            if (currentPopup != null) currentPopup.hide();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/UpdateProfile.fxml"));
            AnchorPane popupContent = loader.load();
            Popup popup = new Popup();
            popup.getContent().add(popupContent);
            popup.setAutoHide(true);
            UpdateAccountController.currentPopup = popup;

            Stage stage = getMainStage();
            if (stage != null)
                popup.show(stage, stage.getX() + stage.getWidth() - 320, stage.getY() + 70);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}