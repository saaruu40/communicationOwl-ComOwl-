package com.example;

import com.example.models.Friend;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;

public class FriendRequestPopupController {

    @FXML
    private ListView<String> requestListView;

    public static Popup currentPopup;

    @FXML
    private void initialize() {
        setupCellFactory();
        loadRequests();
    }

    private void setupCellFactory() {
        requestListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                String[] p = item.split(":");
                String email = p[0];
                String first = p.length > 1 ? p[1] : email;
                String last = p.length > 2 ? p[2] : "";

                // Avatar
                StackPane av = new StackPane();
                av.setPrefSize(38, 38);
                Circle bg = new Circle(19);
                bg.setFill(Color.web("#2a1e44"));
                Label init = new Label(first.isEmpty() ? "?" : first.substring(0, 1).toUpperCase());
                init.setStyle("-fx-text-fill: #c4b0ff; -fx-font-size: 14px; -fx-font-weight: bold;");
                av.getChildren().addAll(bg, init);

                // Name
                Label name = new Label(first + " " + last);
                name.setStyle("-fx-text-fill: #1a1a1a; -fx-font-size: 13px; -fx-font-weight: bold;");
                HBox.setHgrow(name, Priority.ALWAYS);

                // Accept button
                Button accept = new Button("✓ Accept");
                accept.setStyle("-fx-background-color: #7B2CFF; -fx-text-fill: white;"
                        + " -fx-background-radius: 8; -fx-cursor: hand; -fx-font-size: 11px;"
                        + " -fx-padding: 4 10 4 10;");
                accept.setOnMouseClicked(e -> handleAccept(email, accept));

                // Decline button
                Button decline = new Button("✗ Decline");
                decline.setStyle("-fx-background-color: #eee; -fx-text-fill: #666;"
                        + " -fx-background-radius: 8; -fx-cursor: hand; -fx-font-size: 11px;"
                        + " -fx-padding: 4 8 4 8;");
                decline.setOnMouseClicked(e -> handleDecline(email, decline));

                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 10, 6, 10));
                row.getChildren().addAll(av, name, accept, decline);

                setGraphic(row);
                setStyle("-fx-background-color: transparent;");
            }
        });
    }

    private void handleAccept(String senderEmail, Button btn) {
        btn.setDisable(true);
        btn.setText("...");
        new Thread(() -> {
            String resp = SocketClient.send(
                    "ACCEPT_REQUEST|" + senderEmail + "|" + HomeController.currentEmail);
            Platform.runLater(() -> {
                if ("ACCEPT_SUCCESS".equals(resp)) {
                    loadRequests(); // refresh popup list
                    ChatRoomController chat = ChatRoomController.getInstance();
                    if (chat != null)
                        chat.loadFriendRequestBadge();
                    // Refresh Home friend list if open
                    HomeController home = HomeController.getInstance();
                    if (home != null) {
                        home.refreshFriendList();
                        home.loadPendingRequests();
                    }
                } else {
                    btn.setText("✓ Accept");
                    btn.setDisable(false);
                }
            });
        }).start();
    }

    private void handleDecline(String senderEmail, Button btn) {
        btn.setDisable(true);
        new Thread(() -> {
            String resp = SocketClient.send(
                    "DECLINE_REQUEST|" + senderEmail + "|" + HomeController.currentEmail);
            Platform.runLater(() -> {
                if ("DECLINE_SUCCESS".equals(resp)) {
                    loadRequests(); // refresh popup list
                    ChatRoomController chat = ChatRoomController.getInstance();
                    if (chat != null)
                        chat.loadFriendRequestBadge();
                    // Refresh Home pending badge
                    HomeController home = HomeController.getInstance();
                    if (home != null) {
                        home.loadPendingRequests();
                    }
                } else {
                    btn.setDisable(false);
                }
            });
        }).start();
    }

    private void loadRequests() {
        requestListView.getItems().clear();
        new Thread(() -> {
            String resp = SocketClient.send("GET_PENDING|" + HomeController.currentEmail);
            Platform.runLater(() -> {
                if (resp == null || !resp.startsWith("PENDING|"))
                    return;
                String data = resp.substring("PENDING|".length());
                if (data.isBlank() || "EMPTY".equals(data))
                    return;
                for (String r : data.split(",")) {
                    if (!r.isBlank())
                        requestListView.getItems().add(r.trim());
                }
            });
        }).start();
    }

    @FXML
    private void closePopup() {
        if (currentPopup != null)
            currentPopup.hide();
    }
}