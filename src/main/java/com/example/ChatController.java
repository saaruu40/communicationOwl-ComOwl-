package com.example;

import com.example.models.Friend;
import com.example.models.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import javafx.stage.FileChooser;
import com.codes.util.FileMessageCodec;

public class ChatController {

    @FXML
    private ImageView chatUserImage;
    @FXML
    private Label chatUserName;
    @FXML
    private Label chatUserStatus;
    @FXML
    private ScrollPane messageScrollPane;
    @FXML
    private VBox messageContainer;
    @FXML
    private TextField messageField;

    private Friend currentFriend;
    private String currentUserEmail;
    private List<Message> messages = new ArrayList<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a");
    private Timer messagePoller;

    public void setCurrentFriend(Friend friend, String userEmail) {
        this.currentFriend = friend;
        this.currentUserEmail = userEmail;

        Platform.runLater(() -> {
            if (chatUserName != null) {
                chatUserName.setText(friend.getFirstName() + " " + friend.getLastName());
            }
            if (chatUserStatus != null) {
                chatUserStatus.setText(friend.isOnline() ? "Online" : "Offline");
            }
            loadFriendProfilePicture();
            loadChatHistory();
            startMessagePolling();
        });
    }

    private void loadFriendProfilePicture() {
        try {
            Image defaultImage = new Image(getClass().getResourceAsStream("/com/example/Image/default_avatar.png"));
            if (chatUserImage != null) {
                chatUserImage.setImage(defaultImage);
                Circle clip = new Circle(20, 20, 20);
                chatUserImage.setClip(clip);
            }
        } catch (Exception e) {

        }
    }

    private void loadChatHistory() {
        if (messageContainer == null)
            return;

        messageContainer.getChildren().clear();

        new Thread(() -> {
            String response = SocketClient.send("GET_MESSAGES|" + currentUserEmail + "|" + currentFriend.getEmail());

            Platform.runLater(() -> {
                if (response != null && !response.equals("CONNECTION_ERROR") && !response.equals("EMPTY")) {
                    String[] messageLines = response.split("\n");
                    for (String line : messageLines) {
                        String[] parts = line.split("\\|", 4);
                        if (parts.length >= 3) {
                            String sender = parts[0];
                            String content = parts[1];
                            String timestampStr = parts[2];
                            boolean isSent = sender.equals(currentUserEmail);

                            try {
                                LocalDateTime timestamp = LocalDateTime.parse(timestampStr);
                                Message msg = new Message(sender, content, timestamp, isSent);
                                addMessageToUI(msg);
                            } catch (Exception e) {

                                Message msg = new Message(sender, content, LocalDateTime.now(), isSent);
                                addMessageToUI(msg);
                            }
                        }
                    }
                    scrollToBottom();
                } else if (response != null && response.equals("EMPTY")) {
                    // No messages yet, add welcome message
                    Message welcomeMsg = new Message("System", "Start chatting with " +
                            currentFriend.getFirstName() + "!", LocalDateTime.now(), false);
                    addMessageToUI(welcomeMsg);
                }
            });
        }).start();
    }

    private void startMessagePolling() {
        if (messagePoller != null) {
            messagePoller.cancel();
        }

        messagePoller = new Timer();
        messagePoller.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (currentFriend != null && currentUserEmail != null) {
                    pollNewMessages();
                }
            }
        }, 2000, 3000); // Poll every 3 seconds
    }

    private void pollNewMessages() {
        String lastTimestamp = getLastMessageTimestamp();
        if (lastTimestamp.isEmpty())
            return;

        String response = SocketClient.send("GET_MESSAGES_SINCE|" + currentUserEmail + "|" +
                currentFriend.getEmail() + "|" + lastTimestamp);

        if (response != null && !response.equals("CONNECTION_ERROR") && !response.equals("EMPTY")) {
            Platform.runLater(() -> {
                String[] messageLines = response.split("\n");
                for (String line : messageLines) {
                    String[] parts = line.split("\\|", 4);
                    if (parts.length >= 3) {
                        String sender = parts[0];
                        String content = parts[1];
                        String timestampStr = parts[2];
                        boolean isSent = sender.equals(currentUserEmail);

                        // Check if message already exists
                        boolean exists = false;
                        for (Message existing : messages) {
                            if (existing.getText().equals(content) &&
                                    existing.getSender().equals(sender) &&
                                    existing.getFormattedTime().equals(timestampStr)) {
                                exists = true;
                                break;
                            }
                        }

                        if (!exists) {
                            try {
                                LocalDateTime timestamp = LocalDateTime.parse(timestampStr);
                                Message msg = new Message(sender, content, timestamp, isSent);
                                addMessageToUI(msg);

                                // Update inbox preview
                                if (HomeController.getInstance() != null) {
                                    HomeController.getInstance().updateFriendLastMessage(sender, content);
                                }
                            } catch (Exception e) {
                                Message msg = new Message(sender, content, LocalDateTime.now(), isSent);
                                addMessageToUI(msg);
                                
                                // Update inbox preview
                                if (HomeController.getInstance() != null) {
                                    HomeController.getInstance().updateFriendLastMessage(sender, content);
                                }
                            }
                        }
                    }
                }
                scrollToBottom();
            });
        }
    }

    private String getLastMessageTimestamp() {
        if (messages.isEmpty())
            return "";
        Message lastMsg = messages.get(messages.size() - 1);
        if (lastMsg.getTimestamp() == null)
            return "";
        return lastMsg.getTimestamp().toString();
    }

    @FXML
    private void onSendMessage() {
        if (messageField == null)
            return;

        String messageText = messageField.getText().trim();
        if (messageText.isEmpty() || currentFriend == null)
            return;

        // Create message object
        Message message = new Message(
                currentUserEmail,
                messageText,
                LocalDateTime.now(),
                true);

        addMessageToUI(message);
        messageField.clear();
        scrollToBottom();

        new Thread(() -> {
            String response = SocketClient.send("SEND_MESSAGE|" + currentUserEmail + "|" +
                    currentFriend.getEmail() + "|" + messageText);

            if (response != null && response.startsWith("MESSAGE_SENT")) {
                // Update inbox preview
                if (HomeController.getInstance() != null) {
                    HomeController.getInstance().updateFriendLastMessage(currentFriend.getEmail(), messageText);
                }
            } else if (response == null || response.equals("CONNECTION_ERROR") || response.equals("MESSAGE_FAILED")) {
                Platform.runLater(() -> {
                    showErrorMessage("Failed to send message");
                });
            }
        }).start();
    }

    @FXML
    private void onEnterPressed(javafx.scene.input.KeyEvent event) {
        if (event.getCode().toString().equals("ENTER")) {
            onSendMessage();
        }
    }

    private void addMessageToUI(Message message) {
        if (messageContainer == null)
            return;

        messages.add(message);

        HBox messageRow = new HBox();
        messageRow.setPadding(new Insets(5, 0, 5, 0));
        messageRow.setAlignment(message.isSent() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox messageBox = new VBox(4);
        messageBox.setMaxWidth(400);

        Node contentNode;
        if (FileMessageCodec.isFileMessage(message.getText())) {
            try {
                FileMessageCodec.Parsed parsed = FileMessageCodec.decode(message.getText());
                Label fileLabel = new Label("📎 " + parsed.filename());
                fileLabel.setWrapText(true);
                fileLabel.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");
                
                Label mimeLabel = new Label(parsed.mimeType());
                mimeLabel.setStyle("-fx-font-size: 9px; -fx-opacity: 0.7;");
                
                VBox fileInfo = new VBox(2, fileLabel, mimeLabel);
                fileInfo.setStyle(message.isSent() ?
                        "-fx-background-color: linear-gradient(to right, #7B2CFF, #9b4dff); " +
                                "-fx-text-fill: white; -fx-padding: 10px 14px; " +
                                "-fx-background-radius: 18px 18px 4px 18px;" :
                        "-fx-background-color: #1e1038; -fx-text-fill: #f0eaff; " +
                                "-fx-padding: 10px 14px; -fx-background-radius: 18px 18px 18px 4px;");
                
                fileInfo.setOnMouseClicked(e -> {
                    showInfo("File: " + parsed.filename() + " (" + parsed.mimeType() + ")");
                });
                
                contentNode = fileInfo;
            } catch (Exception e) {
                Label errorLabel = new Label("Error decoding file");
                errorLabel.setStyle("-fx-background-color: #ff4d4f; -fx-text-fill: white; -fx-padding: 10px;");
                contentNode = errorLabel;
            }
        } else {
            Label messageLabel = new Label(message.getText());
            messageLabel.setWrapText(true);
            messageLabel.setStyle(message.isSent() ? 
                    "-fx-background-color: linear-gradient(to right, #7B2CFF, #9b4dff); " +
                            "-fx-text-fill: white; -fx-padding: 10px 14px; " +
                            "-fx-background-radius: 18px 18px 4px 18px;" :
                    "-fx-background-color: #1e1038; -fx-text-fill: #f0eaff; " +
                            "-fx-padding: 10px 14px; -fx-background-radius: 18px 18px 18px 4px;");
            contentNode = messageLabel;
        }

        Label timeLabel = new Label(message.getFormattedTime());
        timeLabel.setStyle("-fx-text-fill: #6c5a8e; -fx-font-size: 10px;");
        timeLabel.setAlignment(message.isSent() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        messageBox.getChildren().addAll(contentNode, timeLabel);
        messageRow.getChildren().add(messageBox);
        messageContainer.getChildren().add(messageRow);
        scrollToBottom();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            if (messageScrollPane != null) {
                messageScrollPane.setVvalue(1.0);
            }
        });
    }

    @FXML
    private void onVideoCallClick() {
        if (currentFriend == null)
            return;
        showErrorMessage(
                "Please use the main Chat Room interface to make Video Calls. This view does not support video.");
    }

    @FXML
    private void onAudioCallClick() {
        if (currentFriend == null)
            return;
        showErrorMessage(
                "Please use the main Chat Room interface to make Audio Calls. This view does not support audio.");
    }

    @FXML
    private void onInfoClick() {
        if (currentFriend == null)
            return;
        showInfo("Chat with " + currentFriend.getFirstName());
    }

    @FXML
    private void onEmojiClick() {
        showInfo("Emoji picker coming soon!");
    }

    @FXML
    private void onAttachmentClick() {
        if (currentFriend == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");
        File selectedFile = fileChooser.showOpenDialog(messageScrollPane.getScene().getWindow());

        if (selectedFile != null) {
            // Show a "Sending..." message locally first
            Message sendingMsg = new Message(
                    currentUserEmail,
                    "Sending file: " + selectedFile.getName() + "...",
                    LocalDateTime.now(),
                    true
            );
            
            // Add a small delay or check to prevent accidental double-clicks if needed,
            // but usually showOpenDialog is modal and prevents this.
            
            new Thread(() -> {
                try {
                    byte[] fileData = Files.readAllBytes(selectedFile.toPath());
                    String mimeType = Files.probeContentType(selectedFile.toPath());
                    String encodedFile = FileMessageCodec.encode(selectedFile.getName(), mimeType, fileData);

                    // Send to server
                    String response = SocketClient.send("SEND_MESSAGE|" + currentUserEmail + "|" +
                            currentFriend.getEmail() + "|" + encodedFile);

                    Platform.runLater(() -> {
                        if (response != null && response.startsWith("MESSAGE_SENT")) {
                            // Successfully sent
                            Message fileMsg = new Message(
                                    currentUserEmail,
                                    encodedFile,
                                    LocalDateTime.now(),
                                    true
                            );
                            addMessageToUI(fileMsg);
                            scrollToBottom();
                            
                            // Update inbox preview
                            if (HomeController.getInstance() != null) {
                                HomeController.getInstance().updateFriendLastMessage(currentFriend.getEmail(), encodedFile);
                            }
                        } else {
                            showErrorMessage("Failed to send file: " + response);
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showErrorMessage("Error sending file: " + e.getMessage()));
                }
            }).start();
        }
    }

    private void showInfo(String message) {
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Info");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showErrorMessage(String message) {
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Called by HomeController when a real-time push message arrives
     * for the currently open chat.
     *
     * @param senderEmail the email of the person who sent the message
     * @param text        the message content
     */
    public void receiveMessage(String senderEmail, String text) {
        Message msg = new Message(senderEmail, text, LocalDateTime.now(), false);
        Platform.runLater(() -> {
            addMessageToUI(msg);
            scrollToBottom();
        });
    }

    public void cleanup() {
        if (messagePoller != null) {
            messagePoller.cancel();
            messagePoller = null;
        }
    }
}