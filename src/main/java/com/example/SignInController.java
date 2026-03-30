package com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import java.io.IOException;

public class SignInController {

    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button        signInButton;
    @FXML private Label         errorMessage;

    @FXML
    private void signInHandle() {
        String email    = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter both email and password.");
            return;
        }

        String response = SocketClient.send("LOGIN|" + email + "|" + password);

        if (response == null || response.equals("CONNECTION_ERROR")) {
            showError("Cannot reach the server. Please start the server and try again.");
            return;
        }

        if (response.startsWith("LOGIN_SUCCESS")) {
            String[] parts   = response.split("\\|", 2);
            String firstName = (parts.length > 1 && !parts[1].isBlank()) ? parts[1] : email;

            SocketClient.establishPersistentConnection(email);

            try {
                Stage stage = (Stage) signInButton.getScene().getWindow();

                // No setRoot() — ChatRoom.fxml has its own normal root
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/example/ChatRoom.fxml"));
                AnchorPane root = loader.load();

                ChatRoomController chatController = loader.getController();
                chatController.setCurrentUserInfo(email, firstName);

                stage.setScene(new Scene(root));
                stage.setOnCloseRequest(e -> chatController.cleanup());
                stage.show();

            } catch (IOException e) {
                e.printStackTrace();
                showError("Could not load chat screen: " + e.getMessage());
            }

        } else if (response.startsWith("LOGIN_FAILED")) {
            String[] parts = response.split("\\|", 2);
            showError(parts.length > 1 && !parts[1].isBlank()
                    ? parts[1] : "Invalid email or password.");
        } else {
            showError("Invalid email or password.");
        }
    }

    @FXML
    private void goToSignUp() {
        navigateTo("/com/example/SignUp.fxml", true);
    }

    @FXML
    private void goToForgotPassword() {
        navigateTo("/com/example/ForgetPassword.fxml", true);
    }

    // useRoot=true  → FXML uses <fx:root>, must call setRoot()
    // useRoot=false → FXML has normal <AnchorPane> root, no setRoot()
    private void navigateTo(String fxmlPath, boolean useRoot) {
        try {
            Stage stage = (Stage) signInButton.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            if (useRoot) loader.setRoot(new AnchorPane());
            stage.setScene(new Scene(loader.load()));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showError("Could not load screen.");
        }
    }

    private void showError(String message) {
        errorMessage.setText(message);
        errorMessage.setVisible(true);
    }
}