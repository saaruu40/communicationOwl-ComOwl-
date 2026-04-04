package com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import java.io.IOException;

public class ChangePasswordController {

    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;

    public static Popup currentPopup;

    private Stage getMainStage() {
        return (Stage) Stage.getWindows().stream()
                .filter(w -> w instanceof Stage && w.isShowing())
                .findFirst().orElse(null);
    }

    @FXML
    private void submitPassword() {
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            errorLabel.setText("Please fill in both fields.");
            errorLabel.setVisible(true);
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            errorLabel.setText("Passwords do not match.");
            errorLabel.setVisible(true);
            return;
        }

        String response = SocketClient.send(
                "UPDATE_PASSWORD|" + HomeController.currentEmail
                        + "|" + newPassword + "|" + confirmPassword);
        System.out.println("[ChangePassword] server response: " + response);

        if (isUpdateSuccessful(response)) {
            ChatRoomController chatInstance = ChatRoomController.getInstance();
            if (chatInstance != null) chatInstance.onAccountUpdate();
            errorLabel.setText("Password updated successfully. Logging out...");
            errorLabel.setStyle("-fx-text-fill: green;");
            errorLabel.setVisible(true);

            // Close all popups
            if (currentPopup != null) { currentPopup.hide(); currentPopup = null; }
            ProfileSettingsController.currentPopup = null;
            UpdateAccountController.currentPopup = null;
            ChangeNameController.currentPopup = null;
            ChangeProfilePictureController.currentPopup = null;

            // Force logout then redirect to sign-in page
            SocketClient.send("LOGOUT");
            try {
                Stage stage = getMainStage();
                if (stage != null) {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/SignIn.fxml"));
                    loader.setRoot(new AnchorPane());
                    stage.setScene(new Scene(loader.load()));
                    stage.show();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            errorLabel.setText("Update failed. Try again.");
            errorLabel.setStyle("-fx-text-fill: red;");
            errorLabel.setVisible(true);
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