package com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;

public class HomeController {

    public static String currentEmail;
    public static String currentFirstName;

    @FXML
    private TextField searchField;

    @FXML
    private Label requestBadge;

    @FXML
    private Label notificationBadge;

    @FXML
    private ImageView profileImageView;

    @FXML
    private Label userNameLabel;

    @FXML
    private VBox requestPanel;

    @FXML
    private ListView<String> requestListView;

    @FXML
    private VBox notificationPanel;

    @FXML
    private ListView<String> notificationListView;

    @FXML
    private VBox searchResultPanel;

    @FXML
    private ListView<String> searchResultListView;

    @FXML
    private ListView<String> friendListView;

    @FXML
    private void initialize() {
        // Populate user information from the signed-in session
        if (userNameLabel != null && currentFirstName != null) {
            userNameLabel.setText(currentFirstName);
        }

        // Initialize panels as invisible by default
        if (requestPanel != null) {
            requestPanel.setVisible(false);
            requestPanel.setManaged(false);
        }
        if (notificationPanel != null) {
            notificationPanel.setVisible(false);
            notificationPanel.setManaged(false);
        }
        if (searchResultPanel != null) {
            searchResultPanel.setVisible(false);
            searchResultPanel.setManaged(false);
        }

        // TODO: Load user's profile image
        // TODO: Load friend list
        // TODO: Load pending friend requests
        // TODO: Load notifications
    }

    /**
     * Called when user types in the search field
     */
    @FXML
    private void onSearchTyped(javafx.scene.input.KeyEvent event) {
        String searchText = searchField.getText().trim();
        
        if (searchText.isEmpty()) {
            searchResultPanel.setVisible(false);
            return;
        }

        // Show search results panel
        searchResultPanel.setVisible(true);
        // TODO: Implement actual search functionality
        // This will search for users by email or name
    }

    /**
     * Called when user clicks on friend requests icon
     */
    @FXML
    private void showFriendRequests() {
        requestPanel.setVisible(!requestPanel.isVisible());
    }

    /**
     * Called when user clicks on notification icon
     */
    @FXML
    private void showNotifications() {
        if (notificationPanel != null) {
            boolean isVisible = notificationPanel.isVisible();
            notificationPanel.setVisible(!isVisible);
            notificationPanel.setManaged(!isVisible);
        }

        // Optional: hide request panel when notifications open
        if (notificationPanel != null && notificationPanel.isVisible() && requestPanel != null) {
            requestPanel.setVisible(false);
            requestPanel.setManaged(false);
        }

        if (notificationBadge != null) {
            notificationBadge.setVisible(false);
        }
    }

    /**
     * Called when user clicks on profile image
     */
    @FXML
    private void showProfile() {
        // TODO: Navigate to profile view
        System.out.println("Profile clicked for: " + currentEmail);
    }

    /**
     * Called when user clicks logout
     */
    @FXML
    private void logout() {
        try {
            // Send logout command to server
            if (currentEmail != null) {
                SocketClient.send("LOGOUT|" + currentEmail);
            }
            
            // Close persistent connection
            SocketClient.closePersistentConnection();
            
            // Clear session data
            currentEmail = null;
            currentFirstName = null;
            
            // Return to sign-in screen
            Stage stage = (Stage) userNameLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/SignIn.fxml"));
            loader.setRoot(new javafx.scene.layout.AnchorPane());
            stage.setScene(new javafx.scene.Scene(loader.load()));
            stage.show();
        } catch (IOException e) {
            System.err.println("Error during logout: " + e.getMessage());
            e.printStackTrace();
        }
    }
}