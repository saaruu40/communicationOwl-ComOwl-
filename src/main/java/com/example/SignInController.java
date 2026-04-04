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

        if (response == null || "CONNECTION_ERROR".equals(response)) {
            showError("Cannot reach the server. Please start the server and try again.");
            return;
        }

        if (response.startsWith("LOGIN_SUCCESS")) {
            String[] parts    = response.split("\\|", 2);
            String firstName  = (parts.length > 1 && !parts[1].isBlank()) ? parts[1] : email;

            // ── Save basic session info ──
            HomeController.currentEmail     = email;
            HomeController.currentFirstName = firstName;
            HomeController.currentLastName  = "";
            HomeController.currentProfilePic = "";

            // ── Fetch full profile (lastName + profilePic) ──
            String profileResp = SocketClient.send("GET_USER_INFO|" + email);
            if (profileResp != null && !profileResp.equals("ERROR") && !profileResp.equals("CONNECTION_ERROR")) {
                // Format: firstName|lastName|profilePicBase64
                String[] pp = profileResp.split("\\|", 3);
                if (pp.length > 1) HomeController.currentLastName   = pp[1];
                if (pp.length > 2) HomeController.currentProfilePic = pp[2];
            }

            // ── Establish persistent (keep-alive) connection ──
            SocketClient.establishPersistentConnection(email);

            // ── Navigate to ChatRoom ──
            try {
                Stage stage = (Stage) signInButton.getScene().getWindow();
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/example/ChatRoom.fxml"));
                AnchorPane root = loader.load();

                ChatRoomController chatCtrl = loader.getController();
                chatCtrl.setCurrentUserInfo(email, firstName);

                stage.setScene(new Scene(root));
                stage.setOnCloseRequest(e -> chatCtrl.cleanup());
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

    @FXML private void goToSignUp()           { navigateTo("/com/example/SignUp.fxml",        true);  }
    @FXML private void goToForgotPassword()   { navigateTo("/com/example/ForgetPassword.fxml", true); }

    private void navigateTo(String path, boolean useRoot) {
        try {
            Stage stage = (Stage) signInButton.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource(path));
            if (useRoot) loader.setRoot(new AnchorPane());
            stage.setScene(new Scene(loader.load()));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showError("Could not load screen.");
        }
    }

    private void showError(String msg) {
        errorMessage.setText(msg);
        errorMessage.setVisible(true);
    }
}