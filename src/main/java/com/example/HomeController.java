package com.example;

import com.example.models.Friend;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.stage.Stage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

public class HomeController {

    @FXML private TextField searchField;
    @FXML private ImageView profileImageView;
    @FXML private ImageView sidebarProfileImage;
    @FXML private Label userNameLabel;
    @FXML private Label sidebarUserName;
    @FXML private Label sidebarUserEmail;
    @FXML private Label requestBadge;
    @FXML private Label notificationBadge;
    @FXML private ListView<Friend> friendListView;
    @FXML private ListView<String> searchResultListView;
    @FXML private VBox searchResultPanel;
    @FXML private AnchorPane chatContainer;
    @FXML private VBox noChatPlaceholder;
    @FXML private BorderPane chatView;

    private ChatController chatController;
    private List<Friend> allFriends = new ArrayList<>();
    private List<String> pendingRequests = new ArrayList<>();
    private Friend selectedFriend = null;

    // Static fields from your existing code
    public static String currentEmail;
    public static String currentFirstName;
    public static String currentLastName = "";
    public static String currentProfilePic;

    // Singleton instance for access from other controllers
    private static HomeController instance;

    public static HomeController getInstance() {
        return instance;
    }

    @FXML
    private void initialize() {
        instance = this;
        
        // Set user info
        userNameLabel.setText(currentFirstName);
        sidebarUserName.setText(currentFirstName + " " + currentLastName);
        sidebarUserEmail.setText(currentEmail);

        loadProfilePicture();
        loadFriendList();
        loadPendingRequests();

        setupFriendListView();
        setupSearchResults();

        // Initially hide chat view
        if (chatView != null) {
            chatView.setVisible(false);
            chatView.setManaged(false);
        }
    }

    private void setupFriendListView() {
        friendListView.setCellFactory(lv -> new ListCell<Friend>() {
            @Override
            protected void updateItem(Friend friend, boolean empty) {
                super.updateItem(friend, empty);
                if (empty || friend == null) {
                    setGraphic(null);
                } else {
                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(8, 12, 8, 12));

                    StackPane avatarPane = new StackPane();
                    avatarPane.setPrefSize(48, 48);
                    Circle bg = new Circle(24);
                    bg.setFill(javafx.scene.paint.Color.web("#2a1e44"));
                    String initialText = friend.getFirstName().isEmpty() ? "?" : friend.getFirstName().substring(0, 1).toUpperCase();
                    Label initial = new Label(initialText);
                    initial.setStyle("-fx-text-fill: #c4b0ff; -fx-font-size: 18px; -fx-font-weight: bold;");
                    avatarPane.getChildren().addAll(bg, initial);

                    VBox infoBox = new VBox(4);
                    infoBox.setAlignment(Pos.CENTER_LEFT);
                    HBox.setHgrow(infoBox, Priority.ALWAYS);

                    Label nameLabel = new Label(friend.getFirstName() + " " + friend.getLastName());
                    nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

                    Label lastMessageLabel = new Label(friend.getLastMessage() != null ?
                            friend.getLastMessage() : "Tap to start chatting");
                    lastMessageLabel.setStyle("-fx-text-fill: #bcaeff; -fx-font-size: 11px;");

                    infoBox.getChildren().addAll(nameLabel, lastMessageLabel);

                    VBox rightBox = new VBox(4);
                    rightBox.setAlignment(Pos.CENTER_RIGHT);

                    Circle statusDot = new Circle(6);
                    statusDot.setFill(friend.isOnline() ?
                            javafx.scene.paint.Color.web("#2ecc71") :
                            javafx.scene.paint.Color.web("#7f8c8d"));

                    Label timeLabel = new Label(friend.getLastMessageTime() != null ?
                            friend.getLastMessageTime() : "");
                    timeLabel.setStyle("-fx-text-fill: #6c5a8e; -fx-font-size: 9px;");

                    rightBox.getChildren().addAll(statusDot, timeLabel);

                    row.getChildren().addAll(avatarPane, infoBox, rightBox);

                    setGraphic(row);
                    setStyle("-fx-background-color: transparent; -fx-border-color: #2a1e44; -fx-border-width: 0 0 1 0;");

                    row.setOnMouseClicked(e -> openChat(friend));
                }
            }
        });

        friendListView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                openChat(selected);
            }
        });
    }

    private void openChat(Friend friend) {
        selectedFriend = friend;

        noChatPlaceholder.setVisible(false);
        noChatPlaceholder.setManaged(false);

        if (chatView == null) {
            loadChatView();
        }

        chatView.setVisible(true);
        chatView.setManaged(true);

        if (chatController != null) {
            chatController.setCurrentFriend(friend, currentEmail);
        }
    }

    private void loadChatView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/ChatView.fxml"));
            chatView = loader.load();
            chatController = loader.getController();

            chatContainer.getChildren().add(chatView);
            AnchorPane.setTopAnchor(chatView, 0.0);
            AnchorPane.setBottomAnchor(chatView, 0.0);
            AnchorPane.setLeftAnchor(chatView, 0.0);
            AnchorPane.setRightAnchor(chatView, 0.0);

        } catch (IOException e) {
            e.printStackTrace();
            showError("Failed to load chat view");
        }
    }

    private void setupSearchResults() {
        searchResultListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    String[] parts = item.split(":");
                    String email = parts[0];
                    String firstName = parts.length > 1 ? parts[1] : email;
                    String lastName = parts.length > 2 ? parts[2] : "";

                    HBox row = new HBox(10);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(8, 12, 8, 12));

                    Label nameLabel = new Label(firstName + " " + lastName);
                    nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
                    HBox.setHgrow(nameLabel, Priority.ALWAYS);

                    Button actionButton = new Button();
                    actionButton.setStyle("-fx-background-color: #7B2CFF; -fx-text-fill: white; " +
                            "-fx-background-radius: 15; -fx-padding: 4 12 4 12; -fx-cursor: hand;");

                    checkFriendshipStatus(email, actionButton, firstName);

                    row.getChildren().addAll(nameLabel, actionButton);
                    setGraphic(row);
                }
            }
        });
    }

    private void checkFriendshipStatus(String targetEmail, Button button, String firstName) {
        new Thread(() -> {
            String friendsResponse = SocketClient.send("GET_FRIENDS|" + currentEmail);
            boolean isFriend = false;

            if (friendsResponse != null && friendsResponse.startsWith("FRIENDS|")) {
                String data = friendsResponse.substring("FRIENDS|".length());
                if (!"EMPTY".equals(data) && !data.isEmpty()) {
                    String[] friends = data.split(",");
                    for (String friend : friends) {
                        String[] friendParts = friend.split(":");
                        if (friendParts.length > 0 && friendParts[0].equals(targetEmail)) {
                            isFriend = true;
                            break;
                        }
                    }
                }
            }

            if (isFriend) {
                Platform.runLater(() -> {
                    button.setText("Message");
                    button.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; " +
                            "-fx-background-radius: 15; -fx-padding: 4 12 4 12; -fx-cursor: hand;");
                    button.setOnMouseClicked(e -> {
                        Friend friend = new Friend();
                        friend.setEmail(targetEmail);
                        friend.setFirstName(firstName);
                        openChat(friend);
                    });
                });
                return;
            }

            String pendingResponse = SocketClient.send("GET_PENDING|" + currentEmail);
            boolean requestSent = false;

            if (pendingResponse != null && pendingResponse.startsWith("PENDING|")) {
                String data = pendingResponse.substring("PENDING|".length());
                if (!"EMPTY".equals(data) && !data.isEmpty()) {
                    String[] requests = data.split(",");
                    for (String request : requests) {
                        String[] requestParts = request.split(":");
                        if (requestParts.length > 0 && requestParts[0].equals(targetEmail)) {
                            requestSent = true;
                            break;
                        }
                    }
                }
            }

            final boolean requestAlreadySent = requestSent;

            Platform.runLater(() -> {
                if (requestAlreadySent) {
                    button.setText("Request Sent");
                    button.setDisable(true);
                    button.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; " +
                            "-fx-background-radius: 15; -fx-padding: 4 12 4 12;");
                } else {
                    button.setText("Add Friend");
                    button.setDisable(false);
                    button.setStyle("-fx-background-color: #7B2CFF; -fx-text-fill: white; " +
                            "-fx-background-radius: 15; -fx-padding: 4 12 4 12; -fx-cursor: hand;");
                    button.setOnMouseClicked(e -> sendFriendRequest(targetEmail, button, firstName));
                }
            });
        }).start();
    }

    private void sendFriendRequest(String email, Button button, String firstName) {
        button.setText("Sending...");
        button.setDisable(true);

        new Thread(() -> {
            String response = SocketClient.send("SEND_REQUEST|" + currentEmail + "|" + email);

            Platform.runLater(() -> {
                if ("REQUEST_SENT".equals(response)) {
                    button.setText("Request Sent");
                    button.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; " +
                            "-fx-background-radius: 15; -fx-padding: 4 12 4 12;");
                    showTemporaryMessage("Friend request sent to " + firstName);
                } else if ("ALREADY_FRIENDS".equals(response)) {
                    button.setText("Message");
                    button.setDisable(false);
                    button.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; " +
                            "-fx-background-radius: 15; -fx-padding: 4 12 4 12; -fx-cursor: hand;");
                    Friend friend = new Friend();
                    friend.setEmail(email);
                    friend.setFirstName(firstName);
                    openChat(friend);
                    showTemporaryMessage("You are already friends with " + firstName);
                } else if ("ALREADY_REQUESTED".equals(response)) {
                    button.setText("Request Pending");
                    button.setDisable(true);
                    button.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; " +
                            "-fx-background-radius: 15; -fx-padding: 4 12 4 12;");
                    showTemporaryMessage("Friend request already pending");
                } else {
                    button.setText("Add Friend");
                    button.setDisable(false);
                    button.setStyle("-fx-background-color: #7B2CFF; -fx-text-fill: white; " +
                            "-fx-background-radius: 15; -fx-padding: 4 12 4 12; -fx-cursor: hand;");
                    button.setOnMouseClicked(e -> sendFriendRequest(email, button, firstName));
                    showTemporaryMessage("Failed to send friend request");
                }
            });
        }).start();
    }

    public void loadProfilePicture() {
        if (currentProfilePic != null && !currentProfilePic.isEmpty()) {
            try {
                byte[] imageBytes = Base64.getDecoder().decode(currentProfilePic);
                Image image = new Image(new ByteArrayInputStream(imageBytes));
                profileImageView.setImage(image);
                sidebarProfileImage.setImage(image);

                Circle clip = new Circle(20, 20, 20);
                profileImageView.setClip(clip);

                Circle sidebarClip = new Circle(24, 24, 24);
                sidebarProfileImage.setClip(sidebarClip);
            } catch (Exception e) {
                loadDefaultAvatar();
            }
        } else {
            loadDefaultAvatar();
        }
    }

    private void loadDefaultAvatar() {
        try {
            Image defaultImage = new Image(getClass().getResourceAsStream("/com/example/Image/default_avatar.png"));
            profileImageView.setImage(defaultImage);
            sidebarProfileImage.setImage(defaultImage);
        } catch (Exception e) {
            // Leave empty if default not found
        }
    }

    private void loadFriendList() {
        new Thread(() -> {
            String response = SocketClient.send("GET_FRIENDS|" + currentEmail);

            Platform.runLater(() -> {
                friendListView.getItems().clear();
                allFriends.clear();

                if (response != null && response.startsWith("FRIENDS|")) {
                    String data = response.split("\\|", 2)[1];
                    if (!"EMPTY".equals(data) && !data.isEmpty()) {
                        String[] friends = data.split(",");
                        for (String friendData : friends) {
                            String[] parts = friendData.split(":");
                            if (parts.length >= 3) {
                                Friend friend = new Friend();
                                friend.setEmail(parts[0]);
                                friend.setFirstName(parts[1]);
                                friend.setLastName(parts[2]);
                                friend.setOnline(true);
                                allFriends.add(friend);
                                friendListView.getItems().add(friend);
                            }
                        }
                    }
                }
            });
        }).start();
    }

    // MAKE THIS PUBLIC so FriendRequestPopupController can access it
    public void loadPendingRequests() {
        new Thread(() -> {
            String response = SocketClient.send("GET_PENDING|" + currentEmail);

            Platform.runLater(() -> {
                pendingRequests.clear();

                if (response != null && response.startsWith("PENDING|")) {
                    String data = response.split("\\|", 2)[1];
                    if (!"EMPTY".equals(data) && !data.isEmpty()) {
                        String[] requests = data.split(",");
                        pendingRequests.addAll(Arrays.asList(requests));
                        requestBadge.setText(String.valueOf(requests.length));
                        requestBadge.setVisible(true);
                        return;
                    }
                }
                requestBadge.setVisible(false);
            });
        }).start();
    }

    // Make this available for name/profile updates from child popups
    public void refreshUserInfo() {
        Platform.runLater(() -> {
            if (userNameLabel != null) userNameLabel.setText(currentFirstName);
            if (sidebarUserName != null) sidebarUserName.setText(currentFirstName + " " + currentLastName);
            if (sidebarUserEmail != null) sidebarUserEmail.setText(currentEmail);
            loadProfilePicture();
        });
    }

    // MAKE THIS PUBLIC so FriendRequestPopupController can access it
    public void refreshFriendList() {
        loadFriendList();
        showTemporaryMessage("Refreshing friends list...");
    }

    @FXML
    private void onSearchTyped() {
        String query = searchField.getText().trim();

        if (query.isEmpty()) {
            searchResultPanel.setVisible(false);
            searchResultPanel.setManaged(false);
            return;
        }

        new Thread(() -> {
            String response = SocketClient.send("SEARCH_USER|" + query + "|" + currentEmail);

            Platform.runLater(() -> {
                searchResultListView.getItems().clear();

                if (response != null && response.startsWith("SEARCH_RESULT|")) {
                    String data = response.split("\\|", 2)[1];
                    if (!"EMPTY".equals(data) && !data.isEmpty()) {
                        String[] users = data.split(",");
                        searchResultListView.getItems().addAll(users);
                        searchResultPanel.setVisible(true);
                        searchResultPanel.setManaged(true);
                        return;
                    }
                }
                searchResultPanel.setVisible(false);
                searchResultPanel.setManaged(false);
            });
        }).start();
    }

    @FXML
    private void showFriendRequests() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/FriendRequestPopUp.fxml"));
            AnchorPane popupContent = loader.load();
            Popup popup = new Popup();
            popup.getContent().add(popupContent);
            popup.setAutoHide(true);
            FriendRequestPopupController.currentPopup = popup;

            Stage stage = (Stage) searchField.getScene().getWindow();
            popup.show(stage, stage.getX() + stage.getWidth() - 300, stage.getY() + 70);

            popup.setOnHidden(e -> {
                refreshFriendList();
                loadPendingRequests();
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void showNotifications() {
        showTemporaryMessage("Notifications feature coming soon!");
    }

    @FXML
    private void showProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/ProfileSettings.fxml"));
            AnchorPane popupContent = loader.load();
            Popup popup = new Popup();
            popup.getContent().add(popupContent);
            popup.setAutoHide(true);
            ProfileSettingsController.currentPopup = popup;

            Stage stage = (Stage) searchField.getScene().getWindow();
            popup.show(stage, stage.getX() + stage.getWidth() - 300, stage.getY() + 70);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void logout() {
        SocketClient.send("LOGOUT");
        try {
            Stage stage = (Stage) searchField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/SignIn.fxml"));
            loader.setRoot(new AnchorPane());
            stage.setScene(new Scene(loader.load()));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showTemporaryMessage(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Info");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}