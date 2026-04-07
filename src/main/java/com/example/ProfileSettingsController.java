package com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.stage.Stage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

public class ProfileSettingsController {

    @FXML
    private ImageView profileImageView;
    @FXML
    private ToggleButton notificationToggle;

    public static Popup currentPopup;

    private Stage getMainStage() {
        return (Stage) Stage.getWindows().stream()
                .filter(w -> w instanceof Stage && w.isShowing())
                .findFirst()
                .orElse(null);
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
            Image defaultImage = new Image(
                    getClass().getResourceAsStream(
                            "/com/example/Image/default_avatar.png"));
            profileImageView.setImage(defaultImage);
        }
        profileImageView.setFitWidth(70);
        profileImageView.setFitHeight(70);
        Circle clip = new Circle(35, 35, 35);
        profileImageView.setClip(clip);
    }

    @FXML
    private void closePopup() {
        if (currentPopup != null)
            currentPopup.hide();
    }

    @FXML
    private void showMyProfile() {
        closePopup();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/MyProfile.fxml"));
            AnchorPane popupContent = loader.load();
            Popup popup = new Popup();
            popup.getContent().add(popupContent);
            popup.setAutoHide(true);
            MyProfileController.currentPopup = popup;
            Stage stage = getMainStage();
            if (stage != null)
                popup.show(stage, stage.getX() + stage.getWidth() - 340, stage.getY() + 70);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void showUpdateAccount() {
        closePopup();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/UpdateProfile.fxml"));
            AnchorPane popupContent = loader.load();
            Popup popup = new Popup();
            popup.getContent().add(popupContent);
            popup.setAutoHide(true);
            UpdateAccountController.currentPopup = popup;
            Stage stage = getMainStage();
            if (stage != null)
                popup.show(stage, stage.getX() + stage.getWidth() - 300, stage.getY() + 70);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void showFriends() {
        closePopup();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/FriendListPopUp.fxml"));
            AnchorPane popupContent = loader.load();
            Popup popup = new Popup();
            popup.getContent().add(popupContent);
            popup.setAutoHide(true);
            FriendListPopupController.currentPopup = popup;
            Stage stage = getMainStage();
            if (stage != null)
                popup.show(stage, stage.getX() + stage.getWidth() - 600, stage.getY() + 70);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void logout() {
        closePopup();
        SocketClient.send("LOGOUT");
        try {
            App.setRoot("SignIn");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void toggleNotification() {
        if (notificationToggle.isSelected()) {
            notificationToggle.setText("ON");
            notificationToggle.setStyle(
                    "-fx-background-color: #2ecc71; -fx-background-radius: 20; " +
                            "-fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand;");
        } else {
            notificationToggle.setText("OFF");
            notificationToggle.setStyle(
                    "-fx-background-color: #bbb; -fx-background-radius: 20; " +
                            "-fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand;");
        }
    }

    @FXML
    private void deleteAccount() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Account");
        alert.setHeaderText("Are you sure?");
        alert.setContentText("This will permanently delete your account!");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            logout();
        }
    }
}