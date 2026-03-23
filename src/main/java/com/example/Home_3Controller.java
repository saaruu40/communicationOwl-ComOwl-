package com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

public class HomeController {

    @FXML private TextField  searchField;
    @FXML private ImageView  profileImageView;
    @FXML private Label      userNameLabel;
    @FXML private Label      requestBadge;
    @FXML private Label      notificationBadge;
    @FXML private ListView<String> friendListView;
    @FXML private ListView<String> requestListView;
    @FXML private ListView<String> searchResultListView;
    @FXML private VBox       requestPanel;
    @FXML private VBox       searchResultPanel;


    public static String currentEmail;
    public static String currentFirstName;
    public static String currentProfilePic;

    @FXML
    private void initialize() {

        userNameLabel.setText(currentFirstName);
        loadProfilePicture();


        loadFriendList();
        loadPendingRequests();


        friendListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    String[] parts = item.split(":");
                    String firstName = parts.length > 1 ? parts[1] : item;
                    String lastName  = parts.length > 2 ? parts[2] : "";

                    HBox row = new HBox(10);
                    row.setStyle("-fx-alignment: CENTER_LEFT; -fx-padding: 5;");

                    Label onlineDot = new Label("●");
                    onlineDot.setStyle("-fx-text-fill: #00ff00; -fx-font-size: 10px;");

                    Label name = new Label(firstName + " " + lastName);
                    name.setStyle("-fx-text-fill: white; -fx-font-family: 'Times New Roman';");

                    row.getChildren().addAll(onlineDot, name);
                    setGraphic(row);
                    setStyle("-fx-background-color: transparent;");
                }
            }
        });


        requestListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    String[] parts = item.split(":");
                    String email     = parts[0];
                    String firstName = parts.length > 1 ? parts[1] : email;
                    String lastName  = parts.length > 2 ? parts[2] : "";

                    HBox row = new HBox(8);
                    row.setStyle("-fx-alignment: CENTER_LEFT; -fx-padding: 5;");

                    Label name = new Label(firstName + " " + lastName);
                    name.setStyle("-fx-text-fill: white; -fx-font-family: 'Times New Roman'; -fx-font-size: 12px;");

                    Button acceptBtn = new Button("✓");
                    acceptBtn.setStyle("-fx-background-color: #7B2CFF; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 4;");
                    acceptBtn.setOnMouseClicked(e -> acceptRequest(email));

                    Button declineBtn = new Button("✗");
                    declineBtn.setStyle("-fx-background-color: #3a1a5a; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 4;");
                    declineBtn.setOnMouseClicked(e -> declineRequest(email));

                    row.getChildren().addAll(name, acceptBtn, declineBtn);
                    setGraphic(row);
                    setStyle("-fx-background-color: transparent;");
                }
            }
        });

        // Search Result cell factory
        searchResultListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    String[] parts = item.split(":");
                    String email     = parts[0];
                    String firstName = parts.length > 1 ? parts[1] : email;
                    String lastName  = parts.length > 2 ? parts[2] : "";

                    HBox row = new HBox(8);
                    row.setStyle("-fx-alignment: CENTER_LEFT; -fx-padding: 5;");

                    Label name = new Label(firstName + " " + lastName);
                    name.setStyle("-fx-text-fill: white; -fx-font-family: 'Times New Roman'; -fx-font-size: 12px;");
                    HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);

                    Button addBtn = new Button("Add +");
                    addBtn.setStyle("-fx-background-color: #7B2CFF; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 4; -fx-font-size: 11px;");
                    addBtn.setOnMouseClicked(e -> sendRequest(email, addBtn));

                    row.getChildren().addAll(name, addBtn);
                    setGraphic(row);
                    setStyle("-fx-background-color: transparent;");
                }
            }
        });
    }


    private void loadProfilePicture() {
        if (currentProfilePic != null && !currentProfilePic.isEmpty()) {
            byte[] imageBytes = Base64.getDecoder().decode(currentProfilePic);
            Image image = new Image(new ByteArrayInputStream(imageBytes));
            profileImageView.setImage(image);
        } else {
            Image defaultImage = new Image(
                    getClass().getResourceAsStream("/com/example/Image/default_avatar.png"));
            profileImageView.setImage(defaultImage);
        }
        profileImageView.setFitWidth(40);
        profileImageView.setFitHeight(40);
        Circle clip = new Circle(20, 20, 20);
        profileImageView.setClip(clip);
    }


    private void loadFriendList() {
        String response = SocketClient.send("GET_FRIENDS|" + currentEmail);
        friendListView.getItems().clear();

        if (response != null && response.startsWith("FRIENDS|")) {
            String data = response.split("\\|", 2)[1];
            if (!"EMPTY".equals(data)) {
                String[] friends = data.split(",");
                for (String friend : friends) {
                    friendListView.getItems().add(friend);
                }
            }
        }
    }


    private void loadPendingRequests() {
        String response = SocketClient.send("GET_PENDING|" + currentEmail);
        requestListView.getItems().clear();

        if (response != null && response.startsWith("PENDING|")) {
            String data = response.split("\\|", 2)[1];
            if (!"EMPTY".equals(data)) {
                String[] requests = data.split(",");
                for (String req : requests) {
                    requestListView.getItems().add(req);
                }

                requestBadge.setText(String.valueOf(requests.length));
                requestBadge.setVisible(true);
            } else {
                requestBadge.setVisible(false);
            }
        }
    }

    // Search
    @FXML
    private void onSearchTyped() {
        String query = searchField.getText().trim();
        searchResultListView.getItems().clear();

        if (query.isEmpty()) {
            searchResultPanel.setVisible(false);
            searchResultPanel.setManaged(false);
            return;
        }

        String response = SocketClient.send("SEARCH_USER|" + query + "|" + currentEmail);

        if (response != null && response.startsWith("SEARCH_RESULT|")) {
            String data = response.split("\\|", 2)[1];
            if (!"EMPTY".equals(data)) {
                String[] users = data.split(",");
                for (String user : users) {
                    searchResultListView.getItems().add(user);
                }
                searchResultPanel.setVisible(true);
                searchResultPanel.setManaged(true);
            } else {
                searchResultPanel.setVisible(false);
                searchResultPanel.setManaged(false);
            }
        }
    }


    private void sendRequest(String email, Button btn) {
        String response = SocketClient.send("SEND_REQUEST|" + currentEmail + "|" + email);
        switch (response) {
            case "REQUEST_SENT"      -> { btn.setText("Sent ✓");    btn.setDisable(true); }
            case "ALREADY_FRIENDS"   -> { btn.setText("Friends ✓"); btn.setDisable(true); }
            case "ALREADY_REQUESTED" -> { btn.setText("Pending…");  btn.setDisable(true); }
        }
    }


    private void acceptRequest(String senderEmail) {
        SocketClient.send("ACCEPT_REQUEST|" + senderEmail + "|" + currentEmail);
        loadPendingRequests();
        loadFriendList();
    }


    private void declineRequest(String senderEmail) {
        SocketClient.send("DECLINE_REQUEST|" + senderEmail + "|" + currentEmail);
        loadPendingRequests();
    }


    @FXML
    private void showFriendRequests() {
        boolean isVisible = requestPanel.isVisible();
        requestPanel.setVisible(!isVisible);
        requestPanel.setManaged(!isVisible);
    }


    @FXML
    private void showNotifications() {

    }


    @FXML
    private void showProfile() {

    }


    @FXML
    private void logout() {
        SocketClient.send("LOGOUT");
        try {
            Stage stage = (Stage) searchField.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/SignIn.fxml"));
            loader.setRoot(new AnchorPane());
            stage.setScene(new Scene(loader.load()));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}