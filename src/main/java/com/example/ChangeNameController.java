package com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import java.io.IOException;

public class ChangeNameController {

    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private Label errorLabel;

    public static Popup currentPopup;

    private Stage getMainStage() {
        return (Stage) Stage.getWindows().stream()
                .filter(w -> w instanceof Stage && w.isShowing())
                .findFirst().orElse(null);
    }

    @FXML
    private void initialize() {
        firstNameField.setText(HomeController.currentFirstName);
        lastNameField.setText(HomeController.currentLastName);
    }

    @FXML
    private void submitName() {
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();

        if (firstName.isEmpty() || lastName.isEmpty()) {
            errorLabel.setText("Please fill in both fields.");
            errorLabel.setVisible(true);
            return;
        }

        String response = SocketClient.send(
                "UPDATE_NAME|" + HomeController.currentEmail
                        + "|" + firstName + "|" + lastName);
        System.out.println("[ChangeName] server response: " + response);

        if (isUpdateSuccessful(response)) {
            HomeController.currentFirstName = firstName;
            HomeController.currentLastName = lastName;
            HomeController home = HomeController.getInstance();
            if (home != null) home.refreshUserInfo();
            ChatRoomController chatInstance = ChatRoomController.getInstance();
            if (chatInstance != null) chatInstance.onAccountUpdate();
            errorLabel.setText("Name updated successfully.");
            errorLabel.setStyle("-fx-text-fill: green;");
            errorLabel.setVisible(true);
            goBack();
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
            if (currentPopup != null) { currentPopup.hide(); currentPopup = null; }

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