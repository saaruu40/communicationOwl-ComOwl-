package com.example;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import java.io.IOException;

public class ResetPasswordController {

    public static String pendingEmail;
    public static String pendingAnswer;

    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label         passwordStrengthLabel;
    @FXML private Button        resetPasswordButton;
    @FXML private Label         errorMessage;

    @FXML
    private void initialize() {
        newPasswordField.textProperty().addListener(
                (obs, oldVal, newVal) -> evaluatePasswordStrength(newVal));
    }

    @FXML
    private void resetPasswordHandle() {
        String newPassword     = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            showError("Please fill in both password fields.");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showError("Passwords do not match. Please try again.");
            return;
        }

        if (pendingEmail == null || pendingAnswer == null) {
            showError("Session expired. Please start again.");
            return;
        }

        String command = String.join("|",
                "RESET_PASSWORD",
                pendingEmail,
                pendingAnswer,
                newPassword,
                confirmPassword
        );

        String response = SocketClient.send(command);

        if ("RESET_SUCCESS".equals(response)) {
            pendingEmail  = null;
            pendingAnswer = null;
            goToSignIn();

        } else if ("CONNECTION_ERROR".equals(response)) {
            showError("Cannot reach the server.");
        } else {
            showError(response != null && response.contains("|")
                    ? response.split("\\|", 2)[1]
                    : "Reset failed. Wrong answer.");
        }
    }

    // "Remembered it? Back to Sign In" ক্লিক করলে
    @FXML
    private void backToSignIn() {
        goToSignIn();
    }

    private void goToSignIn() {
        try {
            App.setRoot("SignIn");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void evaluatePasswordStrength(String password) {
        if (password.length() >= 8) {
            passwordStrengthLabel.setText("Password Strength: Strong");
        } else if (password.length() >= 5) {
            passwordStrengthLabel.setText("Password Strength: Medium");
        } else {
            passwordStrengthLabel.setText("Password Strength: Weak");
        }
    }

    private void showError(String msg) {
        errorMessage.setText(msg);
        errorMessage.setVisible(true);
    }
}