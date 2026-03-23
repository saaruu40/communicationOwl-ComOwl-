package com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import java.io.IOException;

public class ForgotPasswordController {

    @FXML private TextField emailField;
    @FXML private Label     recoveryQuestionLabel;  // Label for showing the question
    @FXML private TextField answerField;
    @FXML private Button    recoverButton;
    @FXML private Label     errorMessage;
    @FXML private HBox recoverButtonBox;

    private String verifiedEmail;

    @FXML
    private void initialize() {
        // Hide the question and answer field at the beginning
        recoveryQuestionLabel.setVisible(false);
        answerField.setVisible(false);
        recoverButton.setVisible(false);
        errorMessage.setVisible(false);
    }

    // The question will load automatically when focus leaves the Email field
    @FXML
    private void onEmailTyped() {
        String email = emailField.getText().trim();

        if (email.isEmpty()) {
            recoveryQuestionLabel.setVisible(false);
            answerField.setVisible(false);
            recoverButton.setVisible(false);
            return;
        }

        // Search for the question on the server
        String response = SocketClient.send("FORGOT_QUESTION|" + email);

        if (response != null && response.startsWith("QUESTION|")) {
            String question = response.split("\\|", 2)[1];
            recoveryQuestionLabel.setText("🔒 " + question);
            recoveryQuestionLabel.setVisible(true);
            answerField.setVisible(true);
            recoverButton.setVisible(true);
            recoverButtonBox.setVisible(true);
            recoverButtonBox.setManaged(true);
            errorMessage.setVisible(false);
            verifiedEmail = email;

        } else if ("CONNECTION_ERROR".equals(response)) {
            showError("Cannot reach the server.");
            recoveryQuestionLabel.setVisible(false);
            answerField.setVisible(false);
            recoverButton.setVisible(false);

        } else {
            showError("No account found with this email.");
            recoveryQuestionLabel.setVisible(false);
            answerField.setVisible(false);
            recoverButton.setVisible(false);
        }
    }

    @FXML
    private void recoverPasswordHandle() {
        String answer = answerField.getText().trim();

        if (answer.isEmpty()) {
            showError("Please enter your answer.");
            return;
        }

        // Check the answer on the server
        String response = SocketClient.send("CHECK_ANSWER|" + verifiedEmail + "|" + answer);

        if ("ANSWER_CORRECT".equals(response)) {
            // Correct — go to the ResetPassword page
            errorMessage.setVisible(false);
            ResetPasswordController.pendingEmail  = verifiedEmail;
            ResetPasswordController.pendingAnswer = answer;

            try {
                Stage stage = (Stage) recoverButton.getScene().getWindow();
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/example/ResetPassword.fxml"));
                loader.setRoot(new AnchorPane());
                stage.setScene(new Scene(loader.load()));
                stage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else if ("CONNECTION_ERROR".equals(response)) {
            showError("Cannot reach the server.");
        } else {
            // Wrong answer
            showError("Wrong answer! Please try again.");
        }
    }

    @FXML
    private void backToSignIn() {
        try {
            Stage stage = (Stage) emailField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/SignIn.fxml"));
            loader.setRoot(new AnchorPane());
            stage.setScene(new Scene(loader.load()));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showError(String msg) {
        errorMessage.setText(msg);
        errorMessage.setVisible(true);
    }
}