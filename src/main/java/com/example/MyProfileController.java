package com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.stage.Stage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

public class MyProfileController {

    @FXML private ImageView profileImageView;
    @FXML private Label     firstNameLabel;
    @FXML private Label     lastNameLabel;
    @FXML private Label     emailLabel;

    public static Popup currentPopup;

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
            Image defaultImage = new Image(
                    getClass().getResourceAsStream(
                            "/com/example/Image/default_avatar.png"));
            profileImageView.setImage(defaultImage);
        }
        profileImageView.setFitWidth(90);
        profileImageView.setFitHeight(90);
        Circle clip = new Circle(45, 45, 45);
        profileImageView.setClip(clip);

        firstNameLabel.setText(HomeController.currentFirstName);
        lastNameLabel.setText(HomeController.currentLastName);
        emailLabel.setText(HomeController.currentEmail);
    }

    @FXML
    private void goBack() {
        try {
            if (currentPopup != null) currentPopup.hide();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/ProfileSettings.fxml"));
            AnchorPane popupContent = loader.load();
            Popup popup = new Popup();
            popup.getContent().add(popupContent);
            popup.setAutoHide(true);
            ProfileSettingsController.currentPopup = popup;

            Stage stage = getMainStage();
            if (stage != null)
                popup.show(stage, stage.getX() + stage.getWidth() - 300, stage.getY() + 70);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}