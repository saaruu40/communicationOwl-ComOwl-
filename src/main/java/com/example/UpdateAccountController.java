package com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.io.IOException;

public class UpdateAccountController {

    public static Popup currentPopup;
    
    private Stage getMainStage() {
        return (Stage) Stage.getWindows().stream()
                .filter(w -> w instanceof Stage && w.isShowing())
                .findFirst().orElse(null);
    }
    
    @FXML
    public void goBack() {
        showPopup("/com/example/ProfileSettings.fxml", ProfileSettingsController.class);
    }
    
    @FXML
    public void changeName() {
        showPopup("/com/example/ChangeName.fxml", ChangeNameController.class);
    }
    
    @FXML
    public void changeProfilePicture() {
        showPopup("/com/example/ChangeProfilePicture.fxml", ChangeProfilePictureController.class);
    }
    
    @FXML
    public void changePassword() {
        showPopup("/com/example/ChangePassword.fxml", ChangePasswordController.class);
    }
    
    private void showPopup(String fxml, Class<?> controllerClass) {
        try {
            if (currentPopup != null) {
                currentPopup.hide();
            }
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            AnchorPane popupContent = loader.load();
            
            Popup popup = new Popup();
            popup.getContent().add(popupContent);
            popup.setAutoHide(true);
            
            // Set the static currentPopup reference based on controller type
            Object controller = loader.getController();
            if (controller instanceof ProfileSettingsController) {
                ProfileSettingsController.currentPopup = popup;
            } else if (controller instanceof ChangeNameController) {
                ChangeNameController.currentPopup = popup;
            } else if (controller instanceof ChangePasswordController) {
                ChangePasswordController.currentPopup = popup;
            } else if (controller instanceof ChangeProfilePictureController) {
                ChangeProfilePictureController.currentPopup = popup;
            }
            
            currentPopup = popup;
            
            Stage stage = getMainStage();
            if (stage != null) {
                popup.show(stage, stage.getX() + stage.getWidth() - 320, stage.getY() + 70);
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}