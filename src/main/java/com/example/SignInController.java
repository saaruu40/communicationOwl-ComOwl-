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
            errorMessage.setText("Please enter both email and password.");
            errorMessage.setVisible(true);
            return;
        }

        String response = SocketClient.send("LOGIN|" + email + "|" + password);

        if (response != null && response.startsWith("LOGIN_SUCCESS")) {
            String firstName = response.split("\\|", 2)[1];
            HomeController.currentEmail     = email;
            HomeController.currentFirstName = firstName;

            errorMessage.setVisible(false);
            System.out.println("Welcome, " + firstName + "!");
            try {
                Stage stage = (Stage) signInButton.getScene().getWindow();
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/example/Home_3.fxml"));
                loader.setRoot(new AnchorPane());
                stage.setScene(new Scene(loader.load()));
                stage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if ("CONNECTION_ERROR".equals(response)) {
            errorMessage.setText("Cannot reach the server. Please try again later.");
            errorMessage.setVisible(true);
        } else {
            errorMessage.setText("Invalid email or password.");
            errorMessage.setVisible(true);
        }
    }

    @FXML
    private void goToSignUp() {
        try {
            Stage stage = (Stage) signInButton.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/SignUp.fxml"));
            loader.setRoot(new AnchorPane());
            stage.setScene(new Scene(loader.load()));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void goToForgotPassword() {
        try {
            Stage stage = (Stage) signInButton.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/ForgetPassword.fxml"));
            loader.setRoot(new AnchorPane());
            stage.setScene(new Scene(loader.load()));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}