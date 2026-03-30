package com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.io.IOException;

public class SignUpController {

    @FXML private TextField     firstNameField;
    @FXML private TextField     lastNameField;
    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField     recoveryQuestionField;
    @FXML private TextField     recoveryAnswerField;
    @FXML private Button        signUpButton;
    @FXML private Label         errorMessage;
    @FXML private ImageView     profileImageView;  // Will show a circular image

    private String profilePictureBase64 = "";  // Empty = default image

    @FXML
    private void initialize() {
        Image defaultImage = new Image(
                getClass().getResourceAsStream("/com/example/Image/default_avatar.png"));
        profileImageView.setImage(defaultImage);
        profileImageView.setFitWidth(100);
        profileImageView.setFitHeight(100);
        profileImageView.setPreserveRatio(false);  // ← set to false

        // Circular clip
        Circle clip = new Circle(50, 50, 50);
        profileImageView.setClip(clip);
    }

    @FXML
    private void chooseProfilePicture() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Profile Picture");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));

        Stage stage = (Stage) signUpButton.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            try {
                Image image = new Image(file.toURI().toString());

                // Open the cropper window
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/example/ImageCropper.fxml"));
                AnchorPane cropPane = loader.load();
                ImageCropperController cropController = loader.getController();

                // Callback — will receive the image after cropping
                cropController.setCropCallback((croppedImage, base64) -> {
                    profileImageView.setImage(croppedImage);
                    profileImageView.setFitWidth(100);
                    profileImageView.setFitHeight(100);
                    profileImageView.setPreserveRatio(false);
                    Circle clip = new Circle(50, 50, 50);
                    profileImageView.setClip(clip);
                    profilePictureBase64 = base64;
                });

                cropController.setImage(image);

                Stage cropStage = new Stage();
                cropStage.setTitle("Crop Profile Picture");
                cropStage.setScene(new Scene(cropPane));
                cropStage.initOwner(stage);
                cropStage.show();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    public void SignUpHandle() {
        try {
            System.out.println("SignUpHandle clicked");
            if (firstNameField.getText().isEmpty() || lastNameField.getText().isEmpty() ||
                    emailField.getText().isEmpty() || passwordField.getText().isEmpty() ||
                    confirmPasswordField.getText().isEmpty() || recoveryQuestionField.getText().isEmpty() ||
                    recoveryAnswerField.getText().isEmpty()) {
                showError("All fields must be filled.");
                return;
            }

            if (!passwordField.getText().equals(confirmPasswordField.getText())) {
                showError("Passwords do not match.");
                return;
            }

            String command = String.join("|",
                    "SIGNUP",
                    firstNameField.getText().trim(),
                    lastNameField.getText().trim(),
                    emailField.getText().trim(),
                    passwordField.getText(),
                    confirmPasswordField.getText(),
                    recoveryQuestionField.getText().trim(),
                    recoveryAnswerField.getText().trim(),
                    profilePictureBase64  // If empty, null will be saved on the server
            );

            System.out.println("SIGNUP command: " + command);
            String response = SocketClient.send(command);
            System.out.println("SIGNUP response: " + response);

            if ("SIGNUP_SUCCESS".equals(response)) {
                goToSignIn();
            } else if ("CONNECTION_ERROR".equals(response)) {
                showError("Cannot reach the server.");
            } else {
                showError(response != null && response.contains("|")
                        ? response.split("\\|", 2)[1]
                        : "Sign-up failed. Email may already exist.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError("An unexpected error occurred during signup. Check console.");
        }
    }

    @FXML
    private void goToSignIn() {
        try {
            Stage stage = (Stage) signUpButton.getScene().getWindow();
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