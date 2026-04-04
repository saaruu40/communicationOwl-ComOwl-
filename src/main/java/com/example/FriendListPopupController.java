package com.example;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Popup;
import java.util.ArrayList;
import java.util.List;

public class FriendListPopupController {

    @FXML private ListView<String> friendListView;
    @FXML private TextField        friendSearchField;

    public static Popup currentPopup;
    private List<String> allFriends = new ArrayList<>();

    @FXML
    private void initialize() {
        loadFriendList();

        friendListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    String[] parts    = item.split(":");
                    String firstName  = parts.length > 1 ? parts[1] : item;
                    String lastName   = parts.length > 2 ? parts[2] : "";

                    HBox row = new HBox(8);
                    row.setStyle("-fx-alignment: CENTER_LEFT; -fx-padding: 5;");

                    Label dot = new Label("●");
                    dot.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 10px;");

                    Label name = new Label(firstName + " " + lastName);
                    name.setStyle("-fx-text-fill: #1a1a1a; -fx-font-size: 13px;");
                    HBox.setHgrow(name, Priority.ALWAYS);

                    row.getChildren().addAll(dot, name);
                    setGraphic(row);
                    setStyle("-fx-background-color: transparent;");
                }
            }
        });
    }

    private void loadFriendList() {
        allFriends.clear();
        friendListView.getItems().clear();

        String response = SocketClient.send(
                "GET_FRIENDS|" + HomeController.currentEmail);

        if (response != null && response.startsWith("FRIENDS|")) {
            String data = response.split("\\|", 2)[1];
            if (!"EMPTY".equals(data)) {
                for (String friend : data.split(",")) {
                    allFriends.add(friend);
                    friendListView.getItems().add(friend);
                }
            }
        }
    }

    @FXML
    private void onFriendSearchTyped() {
        String query = friendSearchField.getText().trim().toLowerCase();
        friendListView.getItems().clear();

        if (query.isEmpty()) {
            friendListView.getItems().addAll(allFriends);
            return;
        }

        for (String friend : allFriends) {
            String[] parts    = friend.split(":");
            String firstName  = parts.length > 1 ? parts[1].toLowerCase() : "";
            String lastName   = parts.length > 2 ? parts[2].toLowerCase() : "";
            String email      = parts[0].toLowerCase();

            if (firstName.contains(query) || lastName.contains(query)
                    || email.contains(query)) {
                friendListView.getItems().add(friend);
            }
        }
    }

    @FXML
    private void closePopup() {
        if (currentPopup != null) {
            currentPopup.hide();
        }
    }
}