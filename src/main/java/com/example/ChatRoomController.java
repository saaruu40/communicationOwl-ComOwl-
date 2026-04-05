package com.example;
import com.codes.util.FileMessageCodec;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.util.Duration;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Window;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
public class ChatRoomController {
    // Inner class to store message data for search functionality
    private static class MessageData {
        String text;
        boolean isMe;
        String timestamp;
        String type;
        HBox container; // Reference to the UI element
        MessageData(String text, boolean isMe, String timestamp, String type, HBox container) {
            this.text = text;
            this.isMe = isMe;
            this.timestamp = timestamp;
            this.type = type;
            this.container = container;
        }
    }
    // ── Left Panel ────────────────────────────────────────────────────────────
    @FXML private Pane leftbase;
    @FXML private HBox userCard;
    @FXML private ImageView userImage;
    @FXML private Circle userImageClip;
    @FXML private Label userIdLabel;
    @FXML private TextField globalSearch;
    @FXML private ScrollPane leftpane;
    @FXML private VBox friendslist;
    @FXML private Button usersTabBtn;
    @FXML private Button groupsTabBtn;
    // Top-right notification/friend/profile controls
    @FXML private Button friendRequestsBtn;
    @FXML private Label friendRequestBadge;
    @FXML private Button notificationsBtn;
    @FXML private Label notificationBadge;
    @FXML private Button profileMenuBtn;
    private final Map<String, Integer> unreadMessageCount = new HashMap<>();
    private final Map<String, String> lastMessagePreviews = new HashMap<>();
    private final List<String> conversationEntries = new ArrayList<>();
    private final Set<String> friendEmails = new HashSet<>();
    private final Set<String> sentRequests = new HashSet<>();
    /** email(lower) → decoded Image; avoids re-fetching on every render */
    private final Map<String, Image> profilePictureCache = new HashMap<>();
    private Popup profileSettingsPopup;
    private Popup friendRequestsPopup;

    private javafx.scene.media.MediaPlayer ringtonePlayer;
    private boolean isRingtonePlaying = false;

    // ── Main Chat ─────────────────────────────────────────────────────────────
    @FXML private ImageView chatBG;
    @FXML private ScrollPane chatScroll;
    @FXML private VBox msgbox;
    @FXML private VBox chatbody;
    @FXML private StackPane chatStateOverlay;
    @FXML private VBox chatLoadingPane;
    @FXML private ProgressIndicator chatLoadingIndicator;
    @FXML private Label chatLoadingLabel;
    @FXML private VBox chatErrorPane;
    @FXML private Label chatErrorLabel;
    @FXML private HBox clientCard;
    @FXML private ImageView clientImage;
    @FXML private Circle clientImageClip;
    @FXML private Label clientIdLabel;
    @FXML private StackPane callOverlay;
    @FXML private VBox groupProfilePane;
    // Video call preview components
    private ImageView localVideoView;
    private ImageView remoteVideoView;
    private TextField groupProfileNameField;
    private boolean groupProfileShellBuilt;
    // ── Message Input ─────────────────────────────────────────────────────────
    @FXML private TextField msgField;
    @FXML private Button sendMsg;
    @FXML private Button sendFile;
    @FXML private Button voiceToText;
    @FXML private Button sendEmoji;
    // ── Header Buttons ────────────────────────────────────────────────────────
    @FXML private HBox headerButtonsBox;
    @FXML private Button audiocall;
    @FXML private Button videocall;
    @FXML private Button infobtn;
    @FXML private Button searchBtn;
    @FXML private TextField searchMessages;
    // ── Right Sidebar ─────────────────────────────────────────────────────────
    @FXML private Pane geminiPane;
    @FXML private Pane infoPane;
    @FXML private VBox geminibox;
    @FXML private ScrollPane geminiscrollpane;
    @FXML private TextField geminimsg;
    @FXML private Button geminisend;
    @FXML private Button createGroupBtn;
    @FXML private Label USERNAME;
    @FXML private Button geminiBTN;
    @FXML private Button changeDOBtn;
    @FXML private ImageView userImage1;
    @FXML private Circle userImageClip1;
    // ── Emoji Picker ──────────────────────────────────────────────────────────
    @FXML private Pane emojipane_test;
    @FXML private VBox emojibox;
    @FXML private ScrollPane emojiscroller;
    @FXML private Button closeEmojiPicker;
    // ── State ─────────────────────────────────────────────────────────────────
    private String currentUserEmail;
    private String currentUserName;
    private String selectedChatUserEmail;
    private String selectedChatUserName;
    private String selectedGroupId;
    private String selectedGroupName;
    private boolean isGroupChatActive = false;
    private boolean showingUsersTab = true;
    private String lastMessageTimestamp = null;
    private final List<String> allUsers = new ArrayList<>();
    private final List<String> allGroups = new ArrayList<>();
    private final List<MessageData> allMessages = new ArrayList<>();
    private static ChatRoomController instance;
    private volatile int latestUserRequestId = 0;
    /** Bumped when switching 1:1 or group chat so stale async responses are ignored. */
    private volatile int chatSessionId = 0;
    private Thread messageUpdateThread;
    private volatile boolean isRunning = true;
    // ── Gemini AI Service ──────────────────────────────────────────────────────
    private GeminiChatService geminiChatService;
    // ── Audio / Video Call (1:1) ─────────────────────────────────────────────────
    private final AudioCallService audioCallService = new AudioCallService();
    private final VideoCallService videoCallService = new VideoCallService();
    private volatile String activeCallId = null;
    private volatile boolean callActive = false;
    private volatile boolean videoCall = false;
    private volatile int localAudioPort = -1;
    private volatile int localVideoPort = -1;
    private volatile boolean callUiMonitorRunning = false;
    private volatile Label callStatusLabel;
    private Consumer<String> signalingListener;
    private volatile boolean callSignalingInitialized = false;
    private static String normEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────
    public static ChatRoomController getInstance() {
        return instance;
    }
    @FXML
    public void initialize() {
        instance = this;
        initializeUI();
        loadCurrentUserInfo();
        loadAllUsers();
        loadSentRequests();
        loadConversationList();
        loadFriendRequestBadge();
        loadNotificationBadge();
        loadGroupList();
        initializeEmojiPicker();
        initializeCallSignaling();
        startMessageUpdateThread();
        initializeGeminiService();
   
   
        searchMessages.textProperty().addListener((obs, oldVal, newVal) -> performSearch(newVal));
searchMessages.setOnKeyPressed(e -> {
    if (e.getCode().toString().equals("ESCAPE")) {
        hideSearchField();
    }
});
    }




    @FXML
    private void onSearchButtonClick() {
        if (searchMessages.isVisible()) {
            hideSearchField();
        } else {
            showSearchField();
        }
    }

    private void showSearchField() {
        if (searchMessages != null) {
            searchMessages.setVisible(true);
            searchMessages.setManaged(true);
            searchMessages.requestFocus();
        }
    }

    private void hideSearchField() {
        if (searchMessages != null) {
            searchMessages.setVisible(false);
            searchMessages.setManaged(false);
            searchMessages.clear();
            clearSearchHighlights();
        }
    }

    private void performSearch(String query) {
        clearSearchHighlights();

        if (query == null || query.trim().isEmpty()) {
            return;
        }

        String searchTerm = query.trim().toLowerCase();
        int matchCount = 0;
        HBox firstMatch = null;

        for (MessageData msg : allMessages) {
            if (msg.text != null && msg.text.toLowerCase().contains(searchTerm)) {
                highlightMessage(msg.container, true);
                matchCount++;
                if (firstMatch == null) {
                    firstMatch = msg.container;
                }
            }
        }

        if (firstMatch != null) {
            scrollToMessage(firstMatch);
        }

        System.out.println("[Search] " + matchCount + " message(s) found for: \"" + query + "\"");
    }

    private void highlightMessage(HBox container, boolean highlight) {
        if (highlight) {
            container.setStyle("-fx-background-color: rgba(255, 215, 0, 0.35); -fx-background-radius: 12;");
        } else {
            container.setStyle("");
        }
    }

    private void clearSearchHighlights() {
        for (MessageData msg : allMessages) {
            if (msg.container != null) {
                highlightMessage(msg.container, false);
            }
        }
    }

    private void scrollToMessage(HBox messageContainer) {
        Platform.runLater(() -> {
            int index = msgbox.getChildren().indexOf(messageContainer);
            if (index >= 0) {
                double scrollValue = (double) index / Math.max(1, msgbox.getChildren().size() - 1);
                chatScroll.setVvalue(scrollValue);
            }
        });
    }

    private void initializeCallSignaling() {
        if (callSignalingInitialized) return;
        if (currentUserEmail == null) return;
        signalingListener = this::onSignalingEvent;
        SocketClient.addPersistentListener(signalingListener);
        callSignalingInitialized = true;
        // Bind audio port early so call accept can succeed.
        new Thread(() -> {
            try {
                localAudioPort = audioCallService.bindPort();
                if (SocketClient.isConnected()) {
                    SocketClient.sendPersistent("AUDIO_PORT|" + normEmail(currentUserEmail) + "|" + localAudioPort);
                }
            } catch (Exception e) {
                localAudioPort = -1;
            }
        }, "audio-bind").start();
        // Bind video port early so video call accept can succeed.
        new Thread(() -> {
            try {
                localVideoPort = videoCallService.bindPort();
                if (SocketClient.isConnected()) {
                    SocketClient.sendPersistent("VIDEO_PORT|" + normEmail(currentUserEmail) + "|" + localVideoPort);
                }
            } catch (Exception e) {
                localVideoPort = -1;
            }
        }, "video-bind").start();
    }
    /**
     * Ensures the UDP port is bound and registered with the server.
     * If not yet ready, binds synchronously (blocking the caller).
     * Does NOT open mic/speakers — that happens only in connectToPeer().
     * Returns true if the port is ready, false on failure.
     */
    private boolean ensureAudioPortReady() {
        if (audioCallService.isPortBound() && localAudioPort > 0) return true;
        try {
            localAudioPort = audioCallService.bindPort();
            if (SocketClient.isConnected()) {
                SocketClient.sendPersistent("AUDIO_PORT|" + normEmail(currentUserEmail) + "|" + localAudioPort);
            }
            return localAudioPort > 0;
        } catch (Exception e) {
            localAudioPort = -1;
            return false;
        }
    }
    private boolean ensureVideoPortReady() {
        if (videoCallService.isPortBound() && localVideoPort > 0) return true;
        try {
            localVideoPort = videoCallService.bindPort();
            if (SocketClient.isConnected()) {
                SocketClient.sendPersistent("VIDEO_PORT|" + normEmail(currentUserEmail) + "|" + localVideoPort);
            }
            return localVideoPort > 0;
        } catch (Exception e) {
            localVideoPort = -1;
            return false;
        }
    }
    private void onSignalingEvent(String line) {
        if (line == null || line.isBlank()) return;
        if (currentUserEmail != null && !callSignalingInitialized) {
            initializeCallSignaling();
        }
        System.out.println("[ChatRoom] signaling event: " + line);
        if (line.startsWith("NOTIFICATION|")) {
            handleRealTimeNotification(line);
            return;
        }
        if (!line.startsWith("CALL_") && !line.startsWith("VIDEO_")) return;
        String[] parts = line.split("\\|");
        switch (parts[0]) {
            case "CALL_INVITE_OK" -> {
                // Server confirmed invite deliver; continue waiting for CALL_ESTABLISHED.
            }
            case "CALL_INVITE_OFFLINE" -> {
                Platform.runLater(() -> {
                    endActiveCallIfMatches(activeCallId);
                    hideCallOverlay();
                    showAlert("User is offline (or audio not registered). Please try later.");
                });
            }
            case "CALL_INVITE" -> {
                // CALL_INVITE|callId|callerEmail|callerName
                if (parts.length < 3) return;
                String callId = parts[1];
                String from = parts[2];
                String callerName = parts.length >= 4 ? parts[3] : null;
                if (currentUserEmail == null) return;
                if (Objects.equals(normEmail(from), normEmail(currentUserEmail))) return;
                Platform.runLater(() -> showIncomingCallOverlay(callId, from, callerName, false));
            }
            case "CALL_ESTABLISHED" -> {
                if (parts.length < 4) return;
                String callId = parts[1];
                String peerIp = parts[2];
                int peerPort;
                try { peerPort = Integer.parseInt(parts[3]); } catch (Exception e) { return; }
                // Keep heavy networking code off the FX thread.
                new Thread(() -> startAudioWithPeer(callId, peerIp, peerPort), "call-established-handler").start();
            }
            case "CALL_REJECTED" -> {
                String callId = parts.length > 1 ? parts[1] : null;
                String reason = parts.length > 3 ? parts[3] : "REJECTED";
                Platform.runLater(() -> {
                    endActiveCallIfMatches(callId);
                    hideCallOverlay();
                    showAlert("BUSY".equals(reason) ? "User is busy on another call." : "Call rejected.");
                });
            }
            case "CALL_ACCEPT_NO_AUDIO_PORT" -> {
                Platform.runLater(() -> {
                    endActiveCallIfMatches(activeCallId);
                    hideCallOverlay();
                    showAlert("Audio not ready on one end. Try again.");
                });
            }
            case "CALL_ENDED" -> {
                String callId = parts.length > 1 ? parts[1] : null;
                Platform.runLater(() -> {
                    endActiveCallIfMatches(callId);
                    hideCallOverlay();
                    showAlert("Call ended.");
                });
            }
            case "VIDEO_INVITE_OK" -> {
                // Remote video invite reached peer.
            }
            case "VIDEO_INVITE_OFFLINE" -> {
                Platform.runLater(() -> {
                    endActiveCallIfMatches(activeCallId);
                    hideCallOverlay();
                    showAlert("User is offline (or video not registered). Please try later.");
                });
            }
            case "VIDEO_INVITE" -> {
                // VIDEO_INVITE|videoId|callerEmail|callerName
                if (parts.length < 3) return;
                String videoId = parts[1];
                String from = parts[2];
                String callerName = parts.length >= 4 ? parts[3] : null;
                if (currentUserEmail == null) return;
                if (Objects.equals(normEmail(from), normEmail(currentUserEmail))) return;
                Platform.runLater(() -> showIncomingCallOverlay(videoId, from, callerName, true));
            }
            case "VIDEO_ESTABLISHED" -> {
                if (parts.length < 4) return;
                String videoId = parts[1];
                String peerIp = parts[2];
                int peerPort;
                try { peerPort = Integer.parseInt(parts[3]); } catch (Exception e) { return; }
                new Thread(() -> startVideoWithPeer(videoId, peerIp, peerPort), "video-established-handler").start();
            }
            case "VIDEO_REJECTED" -> {
                String videoId = parts.length > 1 ? parts[1] : null;
                String reason = parts.length > 3 ? parts[3] : "REJECTED";
                Platform.runLater(() -> {
                    endActiveCallIfMatches(videoId);
                    hideCallOverlay();
                    showAlert("BUSY".equals(reason) ? "User is busy on another call." : "Video call rejected.");
                });
            }
            case "VIDEO_ACCEPT_NO_VIDEO_PORT" -> {
                Platform.runLater(() -> {
                    endActiveCallIfMatches(activeCallId);
                    hideCallOverlay();
                    showAlert("Video not ready on one end. Try again.");
                });
            }
            case "VIDEO_ENDED" -> {
                String videoId = parts.length > 1 ? parts[1] : null;
                Platform.runLater(() -> {
                    endActiveCallIfMatches(videoId);
                    hideCallOverlay();
                    showAlert("Video call ended.");
                });
            }
            default -> {
                // No-op for unknown command types
            }
        }
    }
    private void handleRealTimeNotification(String line) {
        System.out.println("[ChatRoom] RT notification received: " + line);
        String[] parts = line.split("\\|", 4);
        if (parts.length < 3) return;
        String type = parts[1];
        String senderEmail = parts.length > 2 ? parts[2] : "";
        String payload = parts.length > 3 ? parts[3] : "";
        if (type.equalsIgnoreCase("FRIEND_REQUEST")) {
            // new friend request -> update badge immediately (no click needed)
            loadFriendRequestBadge();
            friendRequestsBtn.setStyle("-fx-border-color: #ff4d4f; -fx-border-width: 2; -fx-background-color: rgba(255,77,79,0.2);");
        } else if (type.equalsIgnoreCase("FRIEND_REQUEST_ACCEPTED")) {
            // friend request accepted -> update friend list + badge immediately
            loadConversationList();
            loadFriendRequestBadge();
        } else if (type.equalsIgnoreCase("NEW_MESSAGE")) {
            String snippet = payload;
            if (senderEmail != null && !senderEmail.isBlank()) {
                lastMessagePreviews.put(senderEmail, snippet);
                unreadMessageCount.merge(senderEmail, 1, Integer::sum);
                moveConversationToTop(senderEmail);
                if (Objects.equals(normEmail(senderEmail), normEmail(selectedChatUserEmail))) {
                    // if currently chatting with sender, mark as read
                    unreadMessageCount.remove(senderEmail);
                    lastMessageTimestamp = null;
                    pollPrivateMessages();
                }
            }
        } else {
            // no popup behavior for generic notifications
        }
        updateConversationListUI();
    }
    private void moveConversationToTop(String email) {
        if (email == null || email.isBlank()) return;
        String normEmail = normEmail(email);
        int index = -1;
        for (int i = 0; i < conversationEntries.size(); i++) {
            String token = conversationEntries.get(i);
            if (token.toLowerCase().startsWith(normEmail.toLowerCase() + ":")) {
                index = i;
                break;
            }
        }
        if (index == 0) return;
        if (index > 0) {
            String entry = conversationEntries.remove(index);
            conversationEntries.add(0, entry);
        }
        updateConversationListUI();
    }
    private void updateConversationListUI() {
        Platform.runLater(() -> renderUserList(conversationEntries, true));
    }
    // ─────────────────────────────────────────────────────────────────────────
    // UI Setup
    // ─────────────────────────────────────────────────────────────────────────
    private void initializeUI() {
        chatScroll.setFitToWidth(true);
        msgbox.setSpacing(8);
        friendslist.setSpacing(0);
        styleTabBtn(usersTabBtn, true);
        styleTabBtn(groupsTabBtn, false);

        // Auto-scroll to bottom whenever message area grows (new messages added).
        // The listener fires after the layout pass completes, guaranteeing the
        // ScrollPane content size is correct before we move the thumb.
        msgbox.heightProperty().addListener((obs, oldH, newH) -> {
            if (newH.doubleValue() > oldH.doubleValue()) {
                chatScroll.setVvalue(1.0);
            }
        });
    }
    private void styleTabBtn(Button btn, boolean active) {
        if (btn == null) return;
        btn.getStyleClass().removeAll("tab-btn-active", "tab-btn-inactive");
        btn.getStyleClass().add(active ? "tab-btn-active" : "tab-btn-inactive");
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Current User
    // ─────────────────────────────────────────────────────────────────────────
    public void refreshUserInfo() {
        loadCurrentUserInfo();
    }
    private void updateProfileMenuIcon() {
        if (profileMenuBtn == null) return;
        ImageView profileIcon = new ImageView();
        profileIcon.setFitWidth(30);
        profileIcon.setFitHeight(30);
        profileIcon.setPreserveRatio(true);
        profileIcon.setSmooth(true);
        if (userImage != null && userImage.getImage() != null) {
            profileIcon.setImage(userImage.getImage());
        } else if (HomeController.currentProfilePic != null && !HomeController.currentProfilePic.isEmpty()) {
            try {
                byte[] imageBytes = java.util.Base64.getDecoder().decode(HomeController.currentProfilePic);
                profileIcon.setImage(new Image(new java.io.ByteArrayInputStream(imageBytes)));
            } catch (Exception ignored) {
            }
        } else {
            try {
                profileIcon.setImage(new Image(getClass().getResourceAsStream("/com/example/Image/default_avatar.png")));
            } catch (Exception ignored) {
            }
        }
        Circle clip = new Circle(15, 15, 15);
        profileIcon.setClip(clip);
        profileMenuBtn.setGraphic(profileIcon);
        profileMenuBtn.setText("");
        profileMenuBtn.setStyle("-fx-background-color: rgba(123,44,255,0.15); -fx-background-radius: 19; -fx-border-color: rgba(123,44,255,0.25); -fx-border-radius: 19; -fx-border-width: 1;");
        profileMenuBtn.setPrefSize(40, 40);
    }
    private void loadCurrentUserInfo() {
        if (currentUserEmail == null) currentUserEmail = HomeController.currentEmail;
        if (currentUserName == null) currentUserName = HomeController.currentFirstName;
        if (currentUserName != null) {
            if (userIdLabel != null) userIdLabel.setText(currentUserName);
            if (USERNAME != null) USERNAME.setText(currentUserName);
        }
        loadCurrentUserProfileImage(userImage, 40);
        loadCurrentUserProfileImage(userImage1, 80);
        updateProfileMenuIcon();
    }
    public void setCurrentUserInfo(String email, String firstName) {
        this.currentUserEmail = email;
        this.currentUserName = firstName;
        if (userIdLabel != null) userIdLabel.setText(firstName);
        if (USERNAME != null) USERNAME.setText(firstName);
        updateProfileMenuIcon();
        // If controller ran initialize() before setCurrentUserInfo(), ensure calling is initialized now.
        if (!callSignalingInitialized && currentUserEmail != null) {
            initializeCallSignaling();
        }
    }
    public void onAccountUpdate() {
        // Drop stale cache so the updated picture is fetched on next render.
        if (currentUserEmail != null) profilePictureCache.remove(normEmail(currentUserEmail));
        loadCurrentUserInfo();
        loadCurrentUserProfileImage(userImage, 40);
        loadCurrentUserProfileImage(userImage1, 80);
        updateProfileMenuIcon();
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Load Users / Friends / Requests for conversation list
    // ─────────────────────────────────────────────────────────────────────────
    private void loadConversationList() {
        if (currentUserEmail == null) return;
        friendEmails.clear();
        showLoadingLabel("Loading chats...");
        new Thread(() -> {
            String response = SocketClient.send("GET_FRIENDS|" + currentUserEmail);
            Platform.runLater(() -> {
                friendslist.getChildren().clear();
                if (response == null || response.equals("CONNECTION_ERROR") || response.equals("ERROR")) {
                    showErrorLabel("Could not load conversations.");
                    return;
                }
                String data = response.startsWith("FRIENDS|") ? response.substring("FRIENDS|".length()) : response;
                if (data.isBlank() || data.equals("EMPTY")) {
                    showEmptyLabel("No friends yet. Search and add people.");
                    return;
                }
                List<String> friends = new ArrayList<>();
                for (String token : data.split(",")) {
                    if (token.isBlank()) continue;
                    friends.add(token.trim());
                    String[] p = token.split(":", 3);
                    if (p.length > 0) friendEmails.add(p[0].trim());
                }
                conversationEntries.clear();
                conversationEntries.addAll(friends);
                renderUserList(conversationEntries, true);
            });
        }).start();
    }
    private void loadAllUsers() {
        if (currentUserEmail == null) return;
        new Thread(() -> {
            String response = SocketClient.send("GET_ALL_USERS|" + currentUserEmail);
            Platform.runLater(() -> {
                allUsers.clear();
                if (response == null || response.equals("CONNECTION_ERROR") || response.equals("ERROR")) return;
                String data = response.startsWith("ALL_USERS|")
                        ? response.substring("ALL_USERS|".length()) : response;
                if (data.isBlank() || data.equals("EMPTY")) return;
                for (String token : data.split(",")) {
                    if (!token.isBlank()) allUsers.add(token.trim());
                }
            });
        }).start();
    }
    private void loadSentRequests() {
        if (currentUserEmail == null) return;
        sentRequests.clear();
        new Thread(() -> {
            String response = SocketClient.send("GET_SENT_REQUESTS|" + currentUserEmail);
            Platform.runLater(() -> {
                if (response == null || !response.startsWith("SENT|")) return;
                String data = response.substring("SENT|".length());
                if (data.isBlank() || data.equals("EMPTY")) return;
                for (String token : data.split(",")) {
                    if (token.isBlank()) continue;
                    String[] p = token.split(":", 2);
                    if (p.length > 0) sentRequests.add(p[0].trim());
                }
            });
        }).start();
    }
    public void loadFriendRequestBadge() {
        if (currentUserEmail == null || friendRequestBadge == null) return;
        new Thread(() -> {
            String response = SocketClient.send("GET_PENDING|" + currentUserEmail);
            Platform.runLater(() -> {
                int count = 0;
                if (response != null && response.startsWith("PENDING|")) {
                    String data = response.substring("PENDING|".length()).trim();
                    if (!data.isBlank() && !data.equals("EMPTY")) {
                        for (String token : data.split(",")) {
                            if (!token.isBlank()) count++;
                        }
                    }
                }
                friendRequestBadge.setText(String.valueOf(count));
                friendRequestBadge.setVisible(count > 0);
                if (count > 0) {
                    friendRequestsBtn.setStyle("-fx-background-color: rgba(255,77,79,0.2); -fx-border-color: #ff4d4f; -fx-border-width: 2; -fx-border-radius: 19;");
                } else {
                    friendRequestsBtn.setStyle("-fx-background-color: rgba(123,44,255,0.15); -fx-border-color: rgba(123,44,255,0.25); -fx-border-radius: 19; -fx-border-width: 1;");
                }
            });
        }).start();
    }
    public void loadNotificationBadge() {
        if (notificationBadge == null) return;
        int totalUnread = unreadMessageCount.values().stream().mapToInt(Integer::intValue).sum();
        Platform.runLater(() -> {
            notificationBadge.setText(String.valueOf(totalUnread));
            notificationBadge.setVisible(totalUnread > 0);
        });
    }
    private void renderUserList(List<String> users, boolean isConversationList) {
        friendslist.getChildren().clear();
        if (users.isEmpty()) { showEmptyLabel("No users found."); return; }
        for (String u : users) {
            String[] p = u.split(":", 4);
            String email = p.length > 0 ? p[0].trim() : "";
            String first = p.length > 1 ? p[1].trim() : "";
            String last = p.length > 2 ? p[2].trim() : "";
            String pic = p.length > 3 ? p[3].trim() : "";
            boolean isFriend = friendEmails.contains(email);
            boolean isSent = sentRequests.contains(email);
            friendslist.getChildren().add(createUserItem(email, first, last, pic, isFriend, isSent, isConversationList));
        }
    }
    private VBox createUserItem(String email, String firstName, String lastName, String picBase64,
                                boolean isFriend, boolean isSent, boolean isConversationList) {
        String displayName = (firstName.isBlank() ? email.split("@")[0] : (firstName + " " + lastName).trim());
        VBox item = new VBox(4);
        item.setPadding(new Insets(10));
        item.setPrefWidth(300);
        item.getStyleClass().add("friend-card");
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        ImageView avatar = new ImageView();
        avatar.setFitWidth(40);
        avatar.setFitHeight(40);
        avatar.setPreserveRatio(true);
        avatar.setClip(new Circle(20, 20, 20));
        if (!picBase64.isBlank()) {
            loadBase64Image(avatar, picBase64);
            // Seed the cache so selectChat gives an instant header image on click.
            if (!profilePictureCache.containsKey(normEmail(email))) {
                try {
                    byte[] b = Base64.getDecoder().decode(picBase64);
                    Image img = new Image(new java.io.ByteArrayInputStream(b));
                    if (!img.isError()) profilePictureCache.put(normEmail(email), img);
                } catch (Exception ignored) {}
            }
        } else {
            // No pic in GET_FRIENDS payload — async-fetch from server.
            loadProfilePictureForUser(email, avatar, 40);
        }
        Label nameLabel = new Label(displayName);
        nameLabel.setTextFill(Color.web("#f3f4d2"));
        nameLabel.setFont(new Font("DM Sans", 14));
        Label emailLabel = new Label(email);
        emailLabel.setTextFill(Color.web("#a0b9a5"));
        emailLabel.setFont(new Font("DM Sans", 11));
        Label previewLabel = new Label();
        previewLabel.setTextFill(Color.web("#c4c7d6"));
        previewLabel.setFont(new Font("DM Sans", 11));
        int unreadCount = isConversationList ? unreadMessageCount.getOrDefault(email, 0) : 0;
        if (isConversationList && unreadCount > 0) {
            nameLabel.setStyle("-fx-text-fill: #f3f4d2; -fx-font-size: 14px; -fx-font-weight: bold;");
            previewLabel.setStyle("-fx-text-fill: #f7f7ff; -fx-font-weight: bold;");
            previewLabel.setText(lastMessagePreviews.getOrDefault(email, ""));
        } else {
            nameLabel.setStyle("-fx-text-fill: #f3f4d2; -fx-font-size: 14px;");
            previewLabel.setText(lastMessagePreviews.getOrDefault(email, ""));
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label unreadBadge = new Label();
        if (unreadCount > 0) {
            unreadBadge.setText(String.valueOf(unreadCount));
            unreadBadge.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-background-radius: 10; -fx-padding: 2 6 2 6; -fx-font-size: 10px;");
        }
        Button actionBtn = new Button();
        actionBtn.setPrefHeight(30);
        actionBtn.setStyle("-fx-background-radius: 14; -fx-font-size: 11; -fx-cursor: hand;");
        if (isFriend) {
            actionBtn.setText("Message");
            actionBtn.setStyle(actionBtn.getStyle() + " -fx-background-color: #7B2CFF; -fx-text-fill: white;");
            actionBtn.setOnAction(e -> selectChat(email, displayName, avatar));
            item.setOnMouseClicked(e -> selectChat(email, displayName, avatar));
        } else if (isSent) {
            actionBtn.setText("Sent");
            actionBtn.setDisable(true);
            actionBtn.setStyle(actionBtn.getStyle() + " -fx-background-color: #999; -fx-text-fill: white;");
        } else {
            actionBtn.setText("Add Friend");
            actionBtn.setStyle(actionBtn.getStyle() + " -fx-background-color: #22c55e; -fx-text-fill: white;");
            actionBtn.setOnAction(e -> sendFriendRequest(email, actionBtn));
        }
        VBox textBox = new VBox(2, nameLabel, emailLabel, previewLabel);
        row.getChildren().addAll(avatar, textBox, spacer);
        if (unreadCount > 0) row.getChildren().add(unreadBadge);
        row.getChildren().add(actionBtn);
        item.getChildren().add(row);
        return item;
    }
    private void sendFriendRequest(String targetEmail, Button button) {
        if (currentUserEmail == null || targetEmail == null || targetEmail.isBlank()) return;
        button.setDisable(true);
        button.setText("...");
        new Thread(() -> {
            String resp = SocketClient.send("SEND_REQUEST|" + currentUserEmail + "|" + targetEmail);
            Platform.runLater(() -> {
                if ("REQUEST_SENT".equals(resp) || "ALREADY_REQUESTED".equals(resp)) {
                    sentRequests.add(targetEmail);
                    button.setText("Sent");
                    button.setDisable(true);
                    loadFriendRequestBadge();
                } else if ("ALREADY_FRIENDS".equals(resp)) {
                    friendEmails.add(targetEmail);
                    button.setText("Message");
                    button.setDisable(false);
                    button.setOnAction(e -> {
                        selectChat(targetEmail, targetEmail.split("@")[0], null);
                    });
                    loadConversationList();
                } else {
                    button.setText("Add Friend");
                    button.setDisable(false);
                    showAlert("Could not send request: " + resp);
                }
            });
        }).start();
    }
    private void applyItemHover(VBox item, boolean hovered) {
        item.getStyleClass().removeAll("friend-card", "friend-card-hover");
        item.getStyleClass().add(hovered ? "friend-card-hover" : "friend-card");
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Load Groups
    // ─────────────────────────────────────────────────────────────────────────
    private void loadGroupList() {
        if (currentUserEmail == null) return;
        new Thread(() -> {
            String response = SocketClient.send("GET_MY_GROUPS|" + currentUserEmail);
            Platform.runLater(() -> applyMyGroupsResponse(response));
        }).start();
    }
    private void applyMyGroupsResponse(String response) {
        allGroups.clear();
        if (response == null || response.equals("CONNECTION_ERROR") || response.equals("ERROR")) {
            if (!showingUsersTab) renderGroupList(allGroups);
            return;
        }
        String data = response.startsWith("MY_GROUPS|")
                ? response.substring("MY_GROUPS|".length()) : response;
        if (!data.isBlank() && !data.equals("EMPTY")) {
            for (String token : data.split(",")) {
                if (!token.isBlank()) allGroups.add(token.trim());
            }
        }
        if (!showingUsersTab) renderGroupList(allGroups);
    }
    private void renderGroupList(List<String> groups) {
        friendslist.getChildren().clear();
        if (groups.isEmpty()) { showEmptyLabel("No groups yet. Create one!"); return; }
        for (String g : groups) friendslist.getChildren().add(createGroupItem(g));
    }
    private VBox createGroupItem(String groupData) {
        String[] p = groupData.split(":", 2);
        String gId = p.length > 0 ? p[0] : "";
        String gName = p.length > 1 ? p[1] : gId;
        VBox item = new VBox(4);
        item.setPadding(new Insets(10));
        item.setPrefWidth(300);
        item.getStyleClass().add("friend-card");
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("👥");
        icon.setFont(new Font(22));
        Label name = new Label(gName);
        name.setTextFill(Color.web("#f3f4d2"));
        name.setFont(new Font("DM Sans", 14));
        row.getChildren().addAll(icon, name);
        item.getChildren().add(row);
        item.setOnMouseClicked(e -> selectGroupChat(gId, gName));
        item.setOnMouseEntered(e -> applyItemHover(item, true));
        item.setOnMouseExited(e -> applyItemHover(item, false));
        return item;
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Tab Switching
    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    private void onUsersTabClick() {
        showingUsersTab = true;
        styleTabBtn(usersTabBtn, true);
        styleTabBtn(groupsTabBtn, false);
        loadConversationList();
        loadSentRequests();
    }
    @FXML
    private void onGroupsTabClick() {
        showingUsersTab = false;
        styleTabBtn(usersTabBtn, false);
        styleTabBtn(groupsTabBtn, true);
        showLoadingLabel("Loading groups…");
        loadGroupList();
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Search / Filter
    // ─────────────────────────────────────────────────────────────────────────
    private void filterList(String query) {
        // keep compatibility for left search (but removed from popup)
        if (query == null || query.isBlank()) {
            loadConversationList();
            return;
        }
        // search in existing loaded friends by name/email
        List<String> matches = new ArrayList<>();
        String q = query.toLowerCase();
        for (String u : friendEmails) {
            if (u.toLowerCase().contains(q)) {
                matches.add(u);
            }
        }
        renderUserList(matches, true);
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Chat Selection
    // ─────────────────────────────────────────────────────────────────────────
    private void selectChat(String userEmail, String userName, ImageView avatarView) {
        chatSessionId++;
        final int session = chatSessionId;
        isGroupChatActive = false;
        selectedChatUserEmail = userEmail;
        selectedChatUserName = userName;
        selectedGroupId = null;
        lastMessageTimestamp = null;
        clientIdLabel.setText(userName);
        // The row avatar may still show the default if its async fetch is in-flight.
        // Check cache first for an instant correct image.
        String chatKey = normEmail(userEmail);
        if (chatKey != null && profilePictureCache.containsKey(chatKey)) {
            clientImage.setImage(profilePictureCache.get(chatKey));
        } else if (avatarView != null && avatarView.getImage() != null) {
            clientImage.setImage(avatarView.getImage());
        }
        // Always kick a fresh async fetch to keep the header current.
        if (chatKey != null) loadProfilePictureForUser(chatKey, clientImage, 50);
        // Mark this conversation as read when selecting it.
        if (userEmail != null && !userEmail.isBlank()) {
            unreadMessageCount.remove(userEmail);
            updateConversationListUI();
        }
        msgbox.getChildren().clear();
        allMessages.clear(); // Clear stored messages for new chat
        
        msgField.clear();
        hideSearchField(); // Hide search when switching chats
        clearGroupHeaderHandlers();
        showGroupProfilePanel(false);
        loadPrivateChatHistoryInitial(session);
    }
    private void selectGroupChat(String groupId, String groupName) {
        chatSessionId++;
        final int session = chatSessionId;
        isGroupChatActive = true;
        selectedGroupId = groupId;
        selectedGroupName = groupName;
        selectedChatUserEmail = null;
        lastMessageTimestamp = null;
        clientIdLabel.setText("👥 " + groupName);
        clientImage.setImage(null);
        msgbox.getChildren().clear();
        allMessages.clear(); // Clear stored messages for new chat
        msgField.clear();
        hideSearchField(); // Hide search when switching chats
        attachGroupHeaderHandlers();
        loadGroupHeaderFromServerAsync();
        loadGroupChatHistoryInitial(session);
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Group profile (header + overlay panel)
    // ─────────────────────────────────────────────────────────────────────────
    private void clearGroupHeaderHandlers() {
        if (clientIdLabel != null) {
            clientIdLabel.setCursor(Cursor.DEFAULT);
            clientIdLabel.setOnMouseClicked(null);
        }
        if (clientImage != null) {
            clientImage.setCursor(Cursor.DEFAULT);
            clientImage.setOnMouseClicked(null);
        }
    }
    private void attachGroupHeaderHandlers() {
        if (clientIdLabel != null) {
            clientIdLabel.setCursor(Cursor.HAND);
            clientIdLabel.setOnMouseClicked(e -> {
                if (isGroupChatActive) openGroupProfile();
                e.consume();
            });
        }
        if (clientImage != null) {
            clientImage.setCursor(Cursor.HAND);
            clientImage.setOnMouseClicked(e -> {
                if (isGroupChatActive) openGroupProfile();
                e.consume();
            });
        }
    }
    private void loadGroupHeaderFromServerAsync() {
        if (selectedGroupId == null || currentUserEmail == null) return;
        final String gid = selectedGroupId;
        new Thread(() -> {
            String response = SocketClient.send("GET_GROUP_INFO|" + gid + "|" + currentUserEmail);
            Platform.runLater(() -> {
                if (!isGroupChatActive || !Objects.equals(gid, selectedGroupId)) return;
                applyGroupInfoToHeader(response);
            });
        }).start();
    }
    private void applyGroupInfoToHeader(String response) {
        if (response == null || !response.startsWith("GROUP_INFO|")) return;
        String inner = response.substring("GROUP_INFO|".length());
        int sep = inner.indexOf('|');
        String name = sep >= 0 ? inner.substring(0, sep) : inner;
        String pic = sep >= 0 && sep + 1 < inner.length() ? inner.substring(sep + 1) : "";
        selectedGroupName = name;
        if (clientIdLabel != null) clientIdLabel.setText("👥 " + name);
        if (clientImage != null) {
            if (!pic.isBlank()) loadBase64Image(clientImage, pic, 50);
            else {
                clientImage.setImage(null);
                loadProfileImage(clientImage, 50);
            }
        }
    }
    private void showGroupProfilePanel(boolean visible) {
        if (groupProfilePane == null) return;
        groupProfilePane.setVisible(visible);
        groupProfilePane.setManaged(visible);
    }
    private void openGroupProfile() {
        if (!isGroupChatActive || selectedGroupId == null) return;
        ensureGroupProfileShell();
        showGroupProfilePanel(true);
        loadGroupProfileData();
    }
    private void ensureGroupProfileShell() {
        if (groupProfilePane == null || groupProfileShellBuilt) return;
        groupProfileShellBuilt = true;
        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(0, 0, 8, 0));
        Label title = new Label("Group profile");
        title.getStyleClass().add("group-profile-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("Close");
        close.getStyleClass().add("sending-btn");
        close.setOnAction(e -> showGroupProfilePanel(false));
        top.getChildren().addAll(title, spacer, close);
        groupProfileNameField = new TextField();
        groupProfileNameField.getStyleClass().add("text-fields");
        groupProfileNameField.setMaxWidth(Double.MAX_VALUE);
        Button saveName = new Button("Save name");
        saveName.getStyleClass().add("sending-btn");
        saveName.setOnAction(e -> onGroupProfileSaveName());
        Label lblName = new Label("GROUP NAME");
        lblName.getStyleClass().add("dialog-section-label");
        VBox nameBlock = new VBox(6, lblName, groupProfileNameField, saveName);
        nameBlock.setMaxWidth(Double.MAX_VALUE);
        Button seeMem = new Button("See members");
        seeMem.getStyleClass().add("create-group-btn");
        seeMem.setMaxWidth(Double.MAX_VALUE);
        seeMem.setOnAction(e -> {
            if (selectedGroupId != null) showGroupMembersDialog(selectedGroupId);
        });
        Button addMem = new Button("Add members");
        addMem.getStyleClass().add("create-group-btn");
        addMem.setMaxWidth(Double.MAX_VALUE);
        addMem.setOnAction(e -> onGroupProfileAddMembers());
        VBox actions = new VBox(10, seeMem, addMem);
        actions.setFillWidth(true);
        VBox root = new VBox(16, top, nameBlock, actions);
        root.setPadding(new Insets(8, 16, 16, 16));
        root.setMaxWidth(Double.MAX_VALUE);
        root.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(root, Priority.ALWAYS);
        groupProfilePane.getChildren().add(root);
    }
    /** Loads group name for the editor and refreshes the chat header (name + picture). */
    private void loadGroupProfileData() {
        if (selectedGroupId == null || currentUserEmail == null) return;
        final String gid = selectedGroupId;
        new Thread(() -> {
            String info = SocketClient.send("GET_GROUP_INFO|" + gid + "|" + currentUserEmail);
            Platform.runLater(() -> {
                if (!isGroupChatActive || !Objects.equals(gid, selectedGroupId)) return;
                if (info != null && info.startsWith("GROUP_INFO|")) {
                    String inner = info.substring("GROUP_INFO|".length());
                    int sep = inner.indexOf('|');
                    String name = sep >= 0 ? inner.substring(0, sep) : inner;
                    if (groupProfileNameField != null) groupProfileNameField.setText(name);
                    applyGroupInfoToHeader(info);
                }
            });
        }).start();
    }
    /** Themed dialog listing current group members (from server). */
    private void showGroupMembersDialog(String groupId) {
        if (currentUserEmail == null) return;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Group members");
        dialog.initModality(Modality.WINDOW_MODAL);
        Stage owner = getChatOwnerStage();
        if (owner != null) dialog.initOwner(owner);
        dialog.setHeaderText(null);
        dialog.setGraphic(null);
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStyleClass().add("create-group-dialog");
        var css = getClass().getResource("/com/example/chatStyle.css");
        if (css != null) dialogPane.getStylesheets().add(css.toExternalForm());
        Label dlgTitle = new Label("Current members");
        dlgTitle.getStyleClass().add("dialog-title-inline");
        Label subtitle = new Label("Everyone in this group.");
        subtitle.getStyleClass().add("dialog-hint");
        subtitle.setWrapText(true);
        VBox listBox = new VBox(6);
        listBox.setFillWidth(true);
        Label loading = new Label("Loading…");
        loading.getStyleClass().add("dialog-hint");
        listBox.getChildren().add(loading);
        ScrollPane sc = new ScrollPane(listBox);
        sc.setFitToWidth(true);
        sc.setPrefHeight(320);
        sc.setMinHeight(160);
        sc.setMaxWidth(Double.MAX_VALUE);
        sc.getStyleClass().add("create-group-member-scroll");
        VBox form = new VBox(10, dlgTitle, subtitle, sc);
        form.setAlignment(Pos.TOP_LEFT);
        form.setFillWidth(true);
        form.setMinWidth(380);
        form.setPrefWidth(420);
        form.setPadding(new Insets(18, 20, 12, 20));
        VBox.setVgrow(sc, Priority.ALWAYS);
        dialogPane.setContent(form);
        dialogPane.getButtonTypes().setAll(new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.setOnShown(e -> new Thread(() -> {
            String mem = SocketClient.send("GET_GROUP_MEMBERS|" + groupId + "|" + currentUserEmail);
            Platform.runLater(() -> fillMemberListVBoxFromRawResponse(listBox, mem));
        }).start());
        dialog.showAndWait();
    }
    /** Parses {@code GROUP_MEMBERS|…} (or error) into {@code target} rows. */
    private void fillMemberListVBoxFromRawResponse(VBox target, String mem) {
        target.getChildren().clear();
        if (mem == null || "CONNECTION_ERROR".equals(mem) || !mem.startsWith("GROUP_MEMBERS|")) {
            Label err = new Label("Could not load members. Check your connection and try again.");
            err.getStyleClass().add("dialog-hint");
            err.setWrapText(true);
            target.getChildren().add(err);
            return;
        }
        String data = mem.substring("GROUP_MEMBERS|".length()).trim();
        if ("NOT_MEMBER".equals(data)) {
            Label empty = new Label("You are not a member of this group.");
            empty.getStyleClass().add("dialog-hint");
            target.getChildren().add(empty);
            return;
        }
        if ("EMPTY".equals(data) || "ERROR".equals(data) || data.isBlank()) {
            Label empty = new Label("No members yet.");
            empty.getStyleClass().add("dialog-hint");
            target.getChildren().add(empty);
            return;
        }
        for (String token : data.split(",")) {
            if (token.isBlank()) continue;
            String[] p = token.split(":", 3);
            String em = p[0];
            String fn = p.length > 1 ? p[1] : "";
            String ln = p.length > 2 ? p[2] : "";
            String disp = (fn + " " + ln).trim();
            Label lbl = new Label(disp.isBlank() ? em : disp + " · " + em);
            lbl.getStyleClass().add("group-member-row");
            lbl.setWrapText(true);
            lbl.setMaxWidth(400);
            target.getChildren().add(lbl);
        }
    }
    private void onGroupProfileSaveName() {
        if (selectedGroupId == null || currentUserEmail == null || groupProfileNameField == null) return;
        String name = groupProfileNameField.getText().trim();
        if (name.isBlank() || name.contains("|")) {
            showAlert("Enter a valid name (no | character).");
            return;
        }
        final String gid = selectedGroupId;
        new Thread(() -> {
            String r = SocketClient.send("UPDATE_GROUP_NAME|" + gid + "|" + currentUserEmail + "|" + name);
            Platform.runLater(() -> {
                if (r != null && r.startsWith("UPDATE_OK")) {
                    selectedGroupName = name;
                    if (clientIdLabel != null) clientIdLabel.setText("👥 " + name);
                    updateLocalGroupEntryName(gid, name);
                    loadGroupList();
                } else {
                    showAlert("Could not update group name.");
                }
            });
        }).start();
    }
    private void updateLocalGroupEntryName(String groupId, String newName) {
        for (int i = 0; i < allGroups.size(); i++) {
            String g = allGroups.get(i);
            if (g.startsWith(groupId + ":")) {
                allGroups.set(i, groupId + ":" + newName);
                break;
            }
        }
    }
    private void onGroupProfileAddMembers() {
        if (selectedGroupId == null || currentUserEmail == null) return;
        showAddMembersToGroupDialog(selectedGroupId);
    }
    /**
     * Lists users from the DB (SQL LIKE on names/email), excludes current user and existing members.
     * Search field is debounced; checkbox selections persist across refreshes.
     */
    private void showAddMembersToGroupDialog(String groupId) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add members");
        dialog.initModality(Modality.WINDOW_MODAL);
        Stage owner = getChatOwnerStage();
        if (owner != null) dialog.initOwner(owner);
        dialog.setHeaderText(null);
        dialog.setGraphic(null);
        var dialogPane = dialog.getDialogPane();
        dialogPane.getStyleClass().add("create-group-dialog");
        var css = getClass().getResource("/com/example/chatStyle.css");
        if (css != null) dialogPane.getStylesheets().add(css.toExternalForm());
        Label title = new Label("Add members");
        title.getStyleClass().add("dialog-title-inline");
        Label subtitle = new Label(
                "Search by name or email, then tick people to add. Current members are not listed.");
        subtitle.getStyleClass().add("dialog-hint");
        subtitle.setWrapText(true);
        Label lblMembers = new Label("ADD MEMBERS");
        lblMembers.getStyleClass().add("dialog-section-label");
        TextField searchField = new TextField();
        searchField.setPromptText("Search by name or email…");
        searchField.getStyleClass().add("text-fields");
        searchField.setMaxWidth(Double.MAX_VALUE);
        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("dialog-hint");
        statusLabel.setWrapText(true);
        Label listCaption = new Label("People you can add");
        listCaption.getStyleClass().add("dialog-hint");
        VBox listBox = new VBox(8);
        listBox.setFillWidth(true);
        listBox.setPadding(new Insets(4, 0, 0, 0));
        Map<String, Boolean> selection = new HashMap<>();
        final int[] searchSeq = { 0 };
        Runnable runSearch = () -> {
            final int id = ++searchSeq[0];
            String query = searchField.getText() != null ? searchField.getText() : "";
            new Thread(() -> {
                String response = SocketClient.send(
                        "SEARCH_GROUP_ADD_USERS|" + groupId + "|" + currentUserEmail + "|" + query);
                Platform.runLater(() -> {
                    if (id != searchSeq[0]) return;
                    statusLabel.setText("");
                    if (response == null || "CONNECTION_ERROR".equals(response)) {
                        statusLabel.setText("Could not reach the server.");
                        listBox.getChildren().clear();
                        return;
                    }
                    if ("NOT_MEMBER".equals(response)) {
                        statusLabel.setText("You are not a member of this group.");
                        listBox.getChildren().clear();
                        return;
                    }
                    if ("ERROR".equals(response)) {
                        statusLabel.setText("Search failed.");
                        listBox.getChildren().clear();
                        return;
                    }
                    String payload = response.startsWith("GROUP_ADD_USERS|")
                            ? response.substring("GROUP_ADD_USERS|".length()) : response;
                    populateUserCheckboxListFromPayload(listBox, payload, selection);
                });
            }).start();
        };
        PauseTransition debounce = new PauseTransition(Duration.millis(280));
        debounce.setOnFinished(e -> runSearch.run());
        searchField.textProperty().addListener((obs, o, n) -> debounce.playFromStart());
        ScrollPane sc = new ScrollPane(listBox);
        sc.setFitToWidth(true);
        sc.setPrefHeight(240);
        sc.setMinHeight(120);
        sc.setMaxWidth(Double.MAX_VALUE);
        sc.getStyleClass().add("create-group-member-scroll");
        VBox form = new VBox(10);
        form.setAlignment(Pos.TOP_LEFT);
        form.setFillWidth(true);
        form.setMinWidth(380);
        form.setPrefWidth(420);
        form.setPadding(new Insets(18, 20, 12, 20));
        VBox.setVgrow(sc, Priority.ALWAYS);
        form.getChildren().addAll(
                title, subtitle,
                lblMembers, searchField, statusLabel, listCaption, sc);
        dialogPane.setContent(form);
        dialogPane.getButtonTypes().setAll(
                new ButtonType("Add", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.setOnShown(e -> runSearch.run());
        dialog.showAndWait().ifPresent(bt -> {
            if (bt.getButtonData() != ButtonBar.ButtonData.OK_DONE) return;
            List<String> toAdd = new ArrayList<>();
            for (String email : selection.keySet()) {
                if (Boolean.TRUE.equals(selection.get(email))) toAdd.add(email);
            }
            if (toAdd.isEmpty()) return;
            final String gid = groupId;
            new Thread(() -> {
                int fail = 0;
                for (String m : toAdd) {
                    String ar = SocketClient.send("ADD_GROUP_MEMBER|" + gid + "|" + currentUserEmail + "|" + m);
                    if (ar == null || !ar.startsWith("MEMBER_ADDED")) fail++;
                }
                int ff = fail;
                Platform.runLater(() -> {
                    loadGroupProfileData();
                    if (ff > 0) showAlert(ff + " member(s) could not be added.");
                });
            }).start();
        });
    }
    private void populateUserCheckboxListFromPayload(VBox listBox, String payload, Map<String, Boolean> selection) {
        listBox.getChildren().clear();
        if (payload == null || payload.isBlank() || "EMPTY".equals(payload)) {
            Label empty = new Label("No users match your search.");
            empty.getStyleClass().add("dialog-hint");
            listBox.getChildren().add(empty);
            return;
        }
        for (String token : payload.split(",")) {
            if (token.isBlank()) continue;
            String[] p = token.split(":", 4);
            String email = p.length > 0 ? p[0].trim() : "";
            if (email.isEmpty()) continue;
            String first = p.length > 1 ? p[1] : "";
            String last = p.length > 2 ? p[2] : "";
            String disp = (first + " " + last).trim();
            String label = disp.isBlank() ? email : disp + " · " + email;
            CheckBox cb = new CheckBox(label);
            cb.setUserData(email);
            cb.getStyleClass().add("create-group-checkbox");
            cb.setWrapText(true);
            cb.setMaxWidth(Double.MAX_VALUE);
            cb.setSelected(selection.getOrDefault(email, false));
            cb.setOnAction(ev -> selection.put(email, cb.isSelected()));
            listBox.getChildren().add(cb);
        }
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Load Chat History (1-on-1) — background I/O, FX thread UI
    // ─────────────────────────────────────────────────────────────────────────
    private void loadPrivateChatHistoryInitial(int session) {
        if (selectedChatUserEmail == null || currentUserEmail == null) return;
        final String peer = selectedChatUserEmail;
        clearChatError();
        showChatLoading(true, "Loading messages…");
        new Thread(() -> {
            String response = SocketClient.send("GET_MESSAGES|" + currentUserEmail + "|" + peer);
            Platform.runLater(() -> {
                if (session != chatSessionId || !Objects.equals(peer, selectedChatUserEmail)) return;
                showChatLoading(false, null);
                if (response == null || "CONNECTION_ERROR".equals(response)) {
                    showChatError("Could not load messages. Check your connection and try again.");
                    return;
                }
                if (response.isBlank()) {
                    scrollToBottom();
                    return;
                }
                applyPrivateHistoryFromResponse(response, true);
                scrollToBottom();
            });
        }).start();
    }
    private void pollPrivateMessages() {
        if (selectedChatUserEmail == null || currentUserEmail == null || isGroupChatActive) return;
        final int session = chatSessionId;
        final String peer = selectedChatUserEmail;
        final String since = lastMessageTimestamp;
        String cmd = (since == null)
                ? "GET_MESSAGES|" + currentUserEmail + "|" + peer
                : "GET_MESSAGES_SINCE|" + currentUserEmail + "|" + peer + "|" + since;
        new Thread(() -> {
            String response = SocketClient.send(cmd);
            Platform.runLater(() -> {
                if (session != chatSessionId || !Objects.equals(peer, selectedChatUserEmail) || isGroupChatActive)
                    return;
                if (response == null || "CONNECTION_ERROR".equals(response) || response.isBlank()) return;
                applyPrivateHistoryFromResponse(response, since == null);
                scrollToBottom();
            });
        }).start();
    }
    private void applyPrivateHistoryFromResponse(String response, boolean replaceExisting) {
        if (replaceExisting) {
            msgbox.getChildren().clear();
            allMessages.clear();
            lastMessageTimestamp = null;
        }
        for (String line : response.split("\n")) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\|", 4);
            if (parts.length < 2) continue;
            String sender = parts[0];
            String text = parts[1];
            String ts = parts.length > 2 ? parts[2] : "";
            String type = parts.length > 3 ? parts[3] : "text";
            displayMessage(text, sender.equals(currentUserEmail), ts, type);
            lastMessageTimestamp = ts;
        }
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Load Group Chat History — background I/O
    // ─────────────────────────────────────────────────────────────────────────
    private void loadGroupChatHistoryInitial(int session) {
        if (selectedGroupId == null) return;
        final String gid = selectedGroupId;
        clearChatError();
        showChatLoading(true, "Loading group messages…");
        new Thread(() -> {
            String response = SocketClient.send(
                    "GET_GROUP_MESSAGES|" + gid + "|" + currentUserEmail);
            Platform.runLater(() -> {
                if (session != chatSessionId || !Objects.equals(gid, selectedGroupId)) return;
                showChatLoading(false, null);
                if (response == null || "CONNECTION_ERROR".equals(response)) {
                    showChatError("Could not load group messages. Check your connection.");
                    return;
                }
                if ("NOT_MEMBER".equals(response)) {
                    showChatError("You are not a member of this group.");
                    return;
                }
                if (response.isBlank() || "EMPTY".equals(response)) {
                    scrollToBottom();
                    return;
                }
                applyGroupHistoryFromResponse(response, true);
                scrollToBottom();
            });
        }).start();
    }
    private void pollGroupMessages() {
        if (selectedGroupId == null || !isGroupChatActive || currentUserEmail == null) return;
        final int session = chatSessionId;
        final String gid = selectedGroupId;
        final String since = lastMessageTimestamp;
        String cmd = (since == null)
                ? "GET_GROUP_MESSAGES|" + gid + "|" + currentUserEmail
                : "GET_GROUP_MESSAGES_SINCE|" + gid + "|" + since + "|" + currentUserEmail;
        new Thread(() -> {
            String response = SocketClient.send(cmd);
            Platform.runLater(() -> {
                if (session != chatSessionId || !Objects.equals(gid, selectedGroupId) || !isGroupChatActive)
                    return;
                if (response == null || "CONNECTION_ERROR".equals(response)
                        || response.isBlank() || "EMPTY".equals(response)) return;
                if ("NOT_MEMBER".equals(response)) return;
                applyGroupHistoryFromResponse(response, since == null);
                scrollToBottom();
            });
        }).start();
    }
    private void applyGroupHistoryFromResponse(String response, boolean replaceExisting) {
        if (replaceExisting) {
            msgbox.getChildren().clear();
            allMessages.clear();
            lastMessageTimestamp = null;
        }
        for (String line : response.split("\n")) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\|", 3);
            if (parts.length < 2) continue;
            String sender = parts[0];
            String text = parts[1];
            String ts = parts.length > 2 ? parts[2] : "";
            boolean isMe = sender.equals(currentUserEmail);
            String label = isMe ? null : senderDisplayName(sender);
            String mtype = FileMessageCodec.isFileMessage(text) ? "file" : "text";
            displayMessage(text, isMe, ts, mtype, label);
            lastMessageTimestamp = ts;
        }
    }
    private String senderDisplayName(String email) {
        for (String u : allUsers) {
            String[] p = u.split(":", 4);
            if (p.length > 0 && email.equalsIgnoreCase(p[0].trim())) {
                String first = p.length > 1 ? p[1] : "";
                String last = p.length > 2 ? p[2] : "";
                String d = (first + " " + last).trim();
                return d.isBlank() ? shortEmail(email) : d;
            }
        }
        return shortEmail(email);
    }
    private static String shortEmail(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
    private String formatUserLineForEmail(String email) {
        for (String u : allUsers) {
            String[] p = u.split(":", 4);
            if (p.length > 0 && email.equalsIgnoreCase(p[0].trim())) {
                String first = p.length > 1 ? p[1] : "";
                String last = p.length > 2 ? p[2] : "";
                String d = (first + " " + last).trim();
                return d.isBlank() ? email : d + " · " + email;
            }
        }
        return email;
    }
    private void showChatLoading(boolean loading, String statusText) {
        if (chatLoadingPane != null) {
            chatLoadingPane.setVisible(loading);
            chatLoadingPane.setManaged(loading);
        }
        if (chatLoadingLabel != null && statusText != null && !statusText.isBlank())
            chatLoadingLabel.setText(statusText);
        if (chatStateOverlay != null)
            chatStateOverlay.setMouseTransparent(!loading);
        if (chatLoadingIndicator != null)
            chatLoadingIndicator.setVisible(loading);
    }
    private void clearChatError() {
        if (chatErrorPane != null) {
            chatErrorPane.setVisible(false);
            chatErrorPane.setManaged(false);
        }
    }
    private void showChatError(String message) {
        if (chatErrorLabel != null && chatErrorPane != null) {
            chatErrorLabel.setText(message);
            chatErrorPane.setVisible(true);
            chatErrorPane.setManaged(true);
        } else {
            showAlert(message);
        }
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Display Message Bubble
    // ─────────────────────────────────────────────────────────────────────────
    private void displayMessage(String text, boolean isMe, String timestamp, String type) {
        displayMessage(text, isMe, timestamp, type, null);
    }
    /**
     * @param groupSenderLabel if non-null and not from me, shown above the bubble (group chats).
     */
    private void displayMessage(String text, boolean isMe, String timestamp, String type,
                                String groupSenderLabel) {
        if ("file".equals(type) && FileMessageCodec.isFileMessage(text)) {
            displayFileMessage(text, isMe, timestamp, groupSenderLabel);
            return;
        }
        HBox container = new HBox();
        container.setPadding(new Insets(4, 12, 4, 12));
        VBox bubble = new VBox(4);
        Label msgLabel = new Label(text);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(380);
        msgLabel.getStyleClass().add(isMe ? "message-bubble-sent" : "message-bubble-received");
        Label tsLabel = new Label(timestamp);
        tsLabel.getStyleClass().add(isMe ? "message-timestamp-sent" : "message-timestamp-received");
        if (isMe) {
            container.setAlignment(Pos.CENTER_RIGHT);
            bubble.setAlignment(Pos.CENTER_RIGHT);
            tsLabel.setAlignment(Pos.CENTER_RIGHT);
        } else {
            container.setAlignment(Pos.CENTER_LEFT);
            bubble.setAlignment(Pos.CENTER_LEFT);
            tsLabel.setAlignment(Pos.CENTER_LEFT);
        }
        if (groupSenderLabel != null && !groupSenderLabel.isBlank() && !isMe) {
            Label who = new Label(groupSenderLabel);
            who.getStyleClass().add("group-sender-name");
            bubble.getChildren().addAll(who, msgLabel, tsLabel);
        } else {
            bubble.getChildren().addAll(msgLabel, tsLabel);
        }
        container.getChildren().add(bubble);
        msgbox.getChildren().add(container);
        allMessages.add(new MessageData(text, isMe, timestamp, type, container));
    }
    private void displayFileMessage(String encoded, boolean isMe, String timestamp, String groupSenderLabel) {
        FileMessageCodec.Parsed parsed;
        try {
            parsed = FileMessageCodec.decode(encoded);
        } catch (Exception e) {
            displayMessage("[Attachment could not be loaded]", isMe, timestamp, "text", groupSenderLabel);
            return;
        }
        HBox container = new HBox();
        container.setPadding(new Insets(4, 12, 4, 12));
        VBox bubble = new VBox(6);
        bubble.getStyleClass().add(isMe ? "message-bubble-sent" : "message-bubble-received");
        bubble.setPadding(new Insets(10, 12, 10, 12));
        bubble.setMaxWidth(400);
        if (groupSenderLabel != null && !groupSenderLabel.isBlank() && !isMe) {
            Label who = new Label(groupSenderLabel);
            who.getStyleClass().add("group-sender-name");
            bubble.getChildren().add(who);
        }
        Label clip = new Label("📎 " + parsed.filename());
        clip.setWrapText(true);
        clip.getStyleClass().add("file-clip-label");
        Label meta = new Label(parsed.mimeType());
        meta.getStyleClass().add("file-meta-label");
        Button saveBtn = new Button("Save as…");
        saveBtn.getStyleClass().add("file-save-btn");
        saveBtn.setOnAction(ev -> {
            Stage owner = getChatOwnerStage();
            if (owner == null) return;
            FileChooser fc = new FileChooser();
            fc.setTitle("Save file");
            fc.setInitialFileName(parsed.filename());
            java.io.File dest = fc.showSaveDialog(owner);
            if (dest == null) return;
            try {
                Files.write(dest.toPath(), parsed.data());
            } catch (java.io.IOException ioe) {
                showAlert("Could not save file: " + ioe.getMessage());
            }
        });
        Label tsLabel = new Label(timestamp);
        tsLabel.getStyleClass().add(isMe ? "message-timestamp-sent" : "message-timestamp-received");
        bubble.getChildren().addAll(clip, meta, saveBtn, tsLabel);
        if (isMe) {
            container.setAlignment(Pos.CENTER_RIGHT);
            bubble.setAlignment(Pos.CENTER_RIGHT);
            tsLabel.setAlignment(Pos.CENTER_RIGHT);
        } else {
            container.setAlignment(Pos.CENTER_LEFT);
            bubble.setAlignment(Pos.CENTER_LEFT);
            tsLabel.setAlignment(Pos.CENTER_LEFT);
        }
        container.getChildren().add(bubble);
        msgbox.getChildren().add(container);
        allMessages.add(new MessageData(encoded, isMe, timestamp, "file", container));
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Send Message
    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    private void onSendMessageClick() {
        String text = msgField.getText().trim();
        if (text.isEmpty()) return;
        if (isGroupChatActive) sendGroupMessage(text);
        else sendPrivateMessage(text);
    }
    private void sendPrivateMessage(String text) {
        if (selectedChatUserEmail == null) { showAlert("Please select a user to chat with."); return; }
        String cmd = "SEND_MESSAGE|" + currentUserEmail + "|" + selectedChatUserEmail + "|" + text;
        msgField.clear();
        new Thread(() -> {
            String response = SocketClient.send(cmd);
            Platform.runLater(() -> {
                if (response != null && response.startsWith("MESSAGE_SENT")) {
                    // Keep the sender conversation at top and update preview immediately, then refresh history.
                    if (selectedChatUserEmail != null) {
                        lastMessagePreviews.put(selectedChatUserEmail, text);
                        moveConversationToTop(selectedChatUserEmail);
                        loadNotificationBadge();
                    }
                    lastMessageTimestamp = null;
                    pollPrivateMessages();
                } else {
                    showAlert("Failed to send message. Please try again.");
                }
            });
        }).start();
    }
    private void sendGroupMessage(String text) {
        if (selectedGroupId == null) { showAlert("Please select a group to chat with."); return; }
        String cmd = "SEND_GROUP_MESSAGE|" + selectedGroupId + "|" + currentUserEmail + "|" + text;
        msgField.clear();
        new Thread(() -> {
            String response = SocketClient.send(cmd);
            Platform.runLater(() -> {
                if (response != null && response.startsWith("MESSAGE_SENT")) {
                    lastMessageTimestamp = null;
                    pollGroupMessages();
                } else {
                    showAlert("Failed to send group message. You may not be a member of this group.");
                }
            });
        }).start();
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Background Polling (incremental, non-blocking)
    // ─────────────────────────────────────────────────────────────────────────
    private void startMessageUpdateThread() {
        messageUpdateThread = new Thread(() -> {
            while (isRunning) {
                try {
                    Thread.sleep(2000);
                    if (currentUserEmail != null) {
                        String r = SocketClient.send("GET_MY_GROUPS|" + currentUserEmail);
                        Platform.runLater(() -> applyMyGroupsResponse(r));
                    }
                    if (isGroupChatActive && selectedGroupId != null)
                        pollGroupMessages();
                    else if (!isGroupChatActive && selectedChatUserEmail != null)
                        pollPrivateMessages();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        messageUpdateThread.setDaemon(true);
        messageUpdateThread.start();
    }
    public void cleanup() {
        isRunning = false;
        if (messageUpdateThread != null) messageUpdateThread.interrupt();
        
        // Hang up active calls and stop services
        if (callActive && activeCallId != null && selectedChatUserEmail != null) {
            String peer = normEmail(selectedChatUserEmail);
            if (videoCall) {
                SocketClient.sendPersistent("VIDEO_HANGUP|" + activeCallId + "|" + normEmail(currentUserEmail) + "|" + peer);
            } else {
                SocketClient.sendPersistent("CALL_HANGUP|" + activeCallId + "|" + normEmail(currentUserEmail) + "|" + peer);
            }
        }
        
        if (audioCallService != null) audioCallService.stop();
        if (videoCallService != null) videoCallService.stop();
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Emoji Picker
    // ─────────────────────────────────────────────────────────────────────────
    private void initializeEmojiPicker() {
        String[] emojis = {
            "😀","😂","😍","🥰","😢","😡","🎉","🎊",
            "🔥","✨","💯","👍","👏","🙏","❤️","💔",
            "😎","🤔","😴","🥳","😅","🤣","😇","🤩"
        };
        for (String emoji : emojis) {
            Button btn = new Button(emoji);
            btn.setPrefWidth(50);
            btn.setPrefHeight(50);
            btn.setFont(new Font(20));
            btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
            btn.setOnMouseEntered(e ->
                btn.setStyle("-fx-background-color: #e0d8f0; -fx-background-radius: 8; -fx-cursor: hand;"));
            btn.setOnMouseExited(e ->
                btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;"));
            btn.setOnAction(e -> msgField.appendText(emoji));
            emojibox.getChildren().add(btn);
        }
    }
    @FXML private void onEmojiButtonClick() { emojipane_test.setVisible(!emojipane_test.isVisible()); }
    @FXML private void onCloseEmojiPicker() { emojipane_test.setVisible(false); }
    // Stops backdrop click from propagating through the emoji scroll pane
    @FXML
    private void consumeEvent(MouseEvent event) { event.consume(); }
    // ─────────────────────────────────────────────────────────────────────────
    // AI / Gemini Panel
    // ─────────────────────────────────────────────────────────────────────────
    private void initializeGeminiService() {
        try {
            geminiChatService = new GeminiChatService();
            System.out.println("[Gemini] Service initialized successfully.");
        } catch (IllegalStateException e) {
            System.err.println("[Gemini] " + e.getMessage());
            geminiChatService = null;
        }
    }
    @FXML
    private void onGeminiSendClick() {
        String question = geminimsg.getText().trim();
        if (question.isEmpty()) return;
        if (geminiChatService == null) {
            appendGeminiMessage("⚠ AI service unavailable. Set the GEMINI_API_KEY environment variable and restart.", "#8b3a3a");
            return;
        }
        if (currentUserEmail == null) {
            appendGeminiMessage("⚠ Please log in first.", "#8b3a3a");
            return;
        }
        appendGeminiMessage("You: " + question, "#4a7c59");
        geminimsg.clear();
        geminisend.setDisable(true);
        // Show a typing indicator
        Label typingLabel = new Label("✦ AI is thinking...");
        typingLabel.setWrapText(true);
        typingLabel.setMaxWidth(300);
        typingLabel.setFont(new Font("DM Sans", 12));
        typingLabel.setStyle("-fx-padding: 6; -fx-background-radius: 10; " +
                "-fx-background-color: #3d2e5e; -fx-text-fill: #c9b8e8; -fx-font-style: italic;");
        VBox.setMargin(typingLabel, new Insets(4, 8, 4, 8));
        geminibox.getChildren().add(typingLabel);
        if (geminiscrollpane != null) geminiscrollpane.setVvalue(1.0);
        final String userEmail = currentUserEmail;
        new Thread(() -> {
            String answer = geminiChatService.ask(userEmail, question);
            Platform.runLater(() -> {
                geminibox.getChildren().remove(typingLabel);
                appendGeminiMessage("AI: " + answer, "#5e4a7a");
                if (geminiscrollpane != null) geminiscrollpane.setVvalue(1.0);
                geminisend.setDisable(false);
            });
        }, "gemini-ask").start();
    }
    @FXML
    private void onGeminiClearClick() {
        if (geminiChatService != null && currentUserEmail != null) {
            geminiChatService.clearHistory(currentUserEmail);
        }
        geminibox.getChildren().clear();
        appendGeminiMessage("✦ Conversation cleared. Ask me anything!", "#3d2e5e");
    }
    private void appendGeminiMessage(String text, String bg) {
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setMaxWidth(300);
        lbl.setFont(new Font("DM Sans", 13));
        lbl.setStyle("-fx-padding: 8; -fx-background-radius: 10; " +
                     "-fx-background-color: " + bg + "; -fx-text-fill: #f3f4d2;");
        VBox.setMargin(lbl, new Insets(4, 8, 4, 8));
        geminibox.getChildren().add(lbl);
    }
    @FXML
    private void onGeminiButtonClick() {
        boolean vis = geminiPane.isVisible();
        geminiPane.setVisible(!vis);
        infoPane.setVisible(vis);
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Create Group
    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    private void createGroup() {
        if (currentUserEmail == null) return;
        openCreateGroupDialog();
    }
    private void openCreateGroupDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New group chat");
        dialog.initModality(Modality.WINDOW_MODAL);
        if (createGroupBtn != null && createGroupBtn.getScene() != null
                && createGroupBtn.getScene().getWindow() instanceof Stage owner) {
            dialog.initOwner(owner);
        }
        dialog.setHeaderText(null);
        dialog.setGraphic(null);
        var dialogPane = dialog.getDialogPane();
        dialogPane.getStyleClass().add("create-group-dialog");
        var css = getClass().getResource("/com/example/chatStyle.css");
        if (css != null)
            dialogPane.getStylesheets().add(css.toExternalForm());
        Label title = new Label("New group chat");
        title.getStyleClass().add("dialog-title-inline");
        Label subtitle = new Label("Multiple people can chat in one conversation.");
        subtitle.getStyleClass().add("dialog-hint");
        subtitle.setWrapText(true);
        Label lblName = new Label("GROUP NAME");
        lblName.getStyleClass().add("dialog-section-label");
        TextField nameField = new TextField();
        nameField.setPromptText("Enter a name…");
        nameField.getStyleClass().add("text-fields");
        nameField.setMaxWidth(Double.MAX_VALUE);
        Label lblMembers = new Label("ADD MEMBERS");
        lblMembers.getStyleClass().add("dialog-section-label");
        TextField memberSearchField = new TextField();
        memberSearchField.setPromptText("Search by name or email…");
        memberSearchField.getStyleClass().add("text-fields");
        Label memberStatus = new Label();
        memberStatus.getStyleClass().add("dialog-hint");
        memberStatus.setWrapText(true);
        Label listCaption = new Label("People you can add");
        listCaption.getStyleClass().add("dialog-hint");
        VBox memberVBox = new VBox(8);
        memberVBox.setFillWidth(true);
        memberVBox.setPadding(new Insets(4, 0, 0, 0));
        Map<String, Boolean> memberSelection = new HashMap<>();
        final int[] inviteSearchSeq = { 0 };
        Runnable runInviteSearch = () -> {
            final int id = ++inviteSearchSeq[0];
            String q = memberSearchField.getText() != null ? memberSearchField.getText() : "";
            new Thread(() -> {
                String response = SocketClient.send(
                        "SEARCH_USERS_INVITE|" + currentUserEmail + "|" + q);
                Platform.runLater(() -> {
                    if (id != inviteSearchSeq[0]) return;
                    memberStatus.setText("");
                    if (response == null || "CONNECTION_ERROR".equals(response)) {
                        memberStatus.setText("Could not reach the server.");
                        memberVBox.getChildren().clear();
                        return;
                    }
                    if ("ERROR".equals(response)) {
                        memberStatus.setText("Search failed.");
                        memberVBox.getChildren().clear();
                        return;
                    }
                    String payload = response.startsWith("INVITE_USERS|")
                            ? response.substring("INVITE_USERS|".length()) : response;
                    populateUserCheckboxListFromPayload(memberVBox, payload, memberSelection);
                });
            }).start();
        };
        PauseTransition inviteDebounce = new PauseTransition(Duration.millis(280));
        inviteDebounce.setOnFinished(e -> runInviteSearch.run());
        memberSearchField.textProperty().addListener((obs, o, n) -> inviteDebounce.playFromStart());
        ScrollPane memberScroll = new ScrollPane(memberVBox);
        memberScroll.setFitToWidth(true);
        memberScroll.setPrefHeight(240);
        memberScroll.setMinHeight(120);
        memberScroll.setMaxWidth(Double.MAX_VALUE);
        memberScroll.getStyleClass().add("create-group-member-scroll");
        VBox form = new VBox(10);
        form.setAlignment(Pos.TOP_LEFT);
        form.setFillWidth(true);
        form.setMinWidth(380);
        form.setPrefWidth(420);
        form.setPadding(new Insets(18, 20, 12, 20));
        VBox.setVgrow(memberScroll, Priority.ALWAYS);
        form.getChildren().addAll(
                title, subtitle,
                lblName, nameField,
                lblMembers, memberSearchField, memberStatus, listCaption, memberScroll);
        dialogPane.setContent(form);
        dialogPane.getButtonTypes().setAll(
                new ButtonType("Create", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.setOnShown(e -> runInviteSearch.run());
        dialog.showAndWait().ifPresent(bt -> {
            if (bt.getButtonData() != ButtonBar.ButtonData.OK_DONE) return;
            String name = nameField.getText().trim();
            if (name.isBlank()) {
                showAlert("Please enter a group name.");
                return;
            }
            List<String> picked = new ArrayList<>();
            for (Map.Entry<String, Boolean> ent : memberSelection.entrySet()) {
                if (Boolean.TRUE.equals(ent.getValue())) picked.add(ent.getKey());
            }
            createGroupOnServer(name, picked);
        });
    }
    private void createGroupOnServer(String groupName, List<String> memberEmails) {
        String groupId = currentUserEmail + "_" + System.currentTimeMillis();
        new Thread(() -> {
            String r = SocketClient.send(
                    "CREATE_GROUP|" + groupId + "|" + groupName + "|" + currentUserEmail);
            if (r == null || !r.startsWith("GROUP_CREATED")) {
                Platform.runLater(() -> showAlert("Could not create group. Try again."));
                return;
            }
            int failed = 0;
            for (String m : memberEmails) {
                String ar = SocketClient.send(
                        "ADD_GROUP_MEMBER|" + groupId + "|" + currentUserEmail + "|" + m);
                if (ar == null || !ar.startsWith("MEMBER_ADDED")) failed++;
            }
            int failedFinal = failed;
            Platform.runLater(() -> {
                loadGroupList();
                onGroupsTabClick();
                if (failedFinal > 0)
                    showAlert("Group created. " + failedFinal + " member(s) could not be added "
                            + "(invalid user or already in group).");
                else if (memberEmails.isEmpty())
                    showAlert("Group \"" + groupName + "\" created. You're the first member—open the "
                            + "Groups tab and pick it to start chatting.");
                else
                    showAlert("Group \"" + groupName + "\" is ready with " + memberEmails.size()
                            + " invited member(s).");
            });
        }).start();
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Other Button Handlers
    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    private void onSendFileClick() {
        pickAndSendAttachment(false);
    }
    /** @param imageOnly if true, restrict chooser to common image types */
    private void pickAndSendAttachment(boolean imageOnly) {
        if (currentUserEmail == null) {
            showAlert("Not logged in.");
            return;
        }
        if (isGroupChatActive) {
            if (selectedGroupId == null) {
                showAlert("Please select a group.");
                return;
            }
        } else if (selectedChatUserEmail == null) {
            showAlert("Please select a user to chat with.");
            return;
        }
        Stage owner = getChatOwnerStage();
        if (owner == null) {
            showAlert("Window not ready.");
            return;
        }
        FileChooser ch = new FileChooser();
        ch.setTitle(imageOnly ? "Choose image" : "Choose file");
        if (imageOnly) {
            ch.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
        }
        java.io.File f = ch.showOpenDialog(owner);
        if (f == null) return;
        if (!f.isFile() || !f.canRead()) {
            showAlert("Cannot read that file.");
            return;
        }
        if (f.length() > FileMessageCodec.MAX_DECODED_BYTES) {
            showAlert("File is too large (max " + (FileMessageCodec.MAX_DECODED_BYTES / (1024 * 1024)) + " MB).");
            return;
        }
        new Thread(() -> {
            try {
                byte[] data = Files.readAllBytes(f.toPath());
                String mime = Files.probeContentType(f.toPath());
                String payload = FileMessageCodec.encode(f.getName(), mime, data);
                String cmd = isGroupChatActive
                        ? "SEND_GROUP_MESSAGE|" + selectedGroupId + "|" + currentUserEmail + "|" + payload
                        : "SEND_MESSAGE|" + currentUserEmail + "|" + selectedChatUserEmail + "|" + payload;
                String response = SocketClient.send(cmd);
                Platform.runLater(() -> {
                    if (response != null && response.startsWith("MESSAGE_SENT")) {
                        if (isGroupChatActive) pollGroupMessages();
                        else pollPrivateMessages();
                    } else {
                        showAlert("Failed to send file.");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showAlert("Could not send file: " + ex.getMessage()));
            }
        }, "send-file").start();
    }
    @FXML private void onVoiceToTextClick() { showAlert("Voice-to-text coming soon!"); }
    @FXML private void onChangeDisplayPictureClick() { showAlert("Change profile picture coming soon!"); }
    @FXML
    private void onAudioCallClick() {
        if (selectedChatUserEmail == null) { showAlert("Select a user first."); return; }
        if (currentUserEmail == null) { showAlert("Not logged in."); return; }
        if (!SocketClient.isConnected()) { showAlert("Not connected to server."); return; }
        if (callActive && activeCallId != null) {
            // Hang up
            String peer = normEmail(selectedChatUserEmail);
            SocketClient.sendPersistent("CALL_HANGUP|" + activeCallId + "|" + normEmail(currentUserEmail) + "|" + peer);
            endActiveCallIfMatches(activeCallId);
            hideCallOverlay();
            return;
        }
        // Ensure audio port is ready before initiating the call
        showCallingOverlay(selectedChatUserEmail, false);
        new Thread(() -> {
            if (!ensureAudioPortReady()) {
                Platform.runLater(() -> {
                    hideCallOverlay();
                    showAlert("Could not initialize audio (microphone/speaker). Check your audio devices.");
                });
                return;
            }
            String callId = currentUserEmail + "_" + System.currentTimeMillis();
            // Set activeCallId BEFORE sending invite — avoids a race where CALL_ESTABLISHED
            // arrives before Platform.runLater executes and startAudioWithPeer exits early.
            activeCallId = callId;
            callActive = true;
            videoCall = false;
            Platform.runLater(() -> showCallingOverlay(selectedChatUserEmail, false));
            // Include caller name so receiver sees a friendly name in the incoming-call dialog.
            String safeCallerName = (currentUserName != null && !currentUserName.isBlank())
                    ? currentUserName : normEmail(currentUserEmail);
            SocketClient.sendPersistent("CALL_INVITE|" + normEmail(currentUserEmail)
                    + "|" + normEmail(selectedChatUserEmail)
                    + "|" + callId
                    + "|" + safeCallerName);
        }, "audio-call-init").start();
    }
    @FXML
    private void onVideoCallClick() {
        if (selectedChatUserEmail == null) { showAlert("Select a user first."); return; }
        if (currentUserEmail == null) { showAlert("Not logged in."); return; }
        if (!SocketClient.isConnected()) { showAlert("Not connected to server."); return; }
        if (callActive && activeCallId != null) {
            String peer = normEmail(selectedChatUserEmail);
            SocketClient.sendPersistent("VIDEO_HANGUP|" + activeCallId + "|" + normEmail(currentUserEmail) + "|" + peer);
            endActiveCallIfMatches(activeCallId);
            hideCallOverlay();
            return;
        }
        videoCall = true;
        showCallingOverlay(selectedChatUserEmail, true);
        new Thread(() -> {
            if (!ensureVideoPortReady()) {
                Platform.runLater(() -> {
                    hideCallOverlay();
                    showAlert("Could not initialize camera/microphone for video.");
                });
                return;
            }
            String videoId = currentUserEmail + "_" + System.currentTimeMillis();
            // Set activeCallId BEFORE sending invite to avoid race with VIDEO_ESTABLISHED
            activeCallId = videoId;
            callActive = true;
            videoCall = true;
            Platform.runLater(() -> showCallingOverlay(selectedChatUserEmail, true));
            String safeCallerNameV = (currentUserName != null && !currentUserName.isBlank())
                    ? currentUserName : normEmail(currentUserEmail);
            SocketClient.sendPersistent("VIDEO_INVITE|" + normEmail(currentUserEmail)
                    + "|" + normEmail(selectedChatUserEmail)
                    + "|" + videoId
                    + "|" + safeCallerNameV);
        }, "video-call-init").start();
    }
    private void showCallingOverlay(String to, boolean video) {
        if (callOverlay == null) return;
        callOverlay.getChildren().clear();
        Label icon = new Label(video ? "📹" : "📞");
        icon.setFont(Font.font(32));
        String displayName = selectedChatUserName != null ? selectedChatUserName : senderDisplayName(to);
        Label heading = new Label((video ? "Video call to " : "Calling ") + displayName + "…");
        heading.setTextFill(Color.WHITE);
        heading.setFont(Font.font("DM Sans", 18));
        Label status = new Label("Ringing…");
        status.setTextFill(Color.web("#d8dee9"));
        status.setFont(Font.font("DM Sans", 13));
        callStatusLabel = status;
        Button hangup = new Button("✕ Hang up");
        hangup.getStyleClass().add("call-btn");
        hangup.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 24; -fx-font-size: 14; -fx-cursor: hand;");
        hangup.setOnAction(e -> {
            if (activeCallId != null) {
                if (videoCall) {
                    SocketClient.sendPersistent("VIDEO_HANGUP|" + activeCallId + "|" + normEmail(currentUserEmail) + "|" + normEmail(to));
                } else {
                    SocketClient.sendPersistent("CALL_HANGUP|" + activeCallId + "|" + normEmail(currentUserEmail) + "|" + normEmail(to));
                }
            }
            endActiveCallIfMatches(activeCallId);
            hideCallOverlay();
        });
        VBox box = new VBox(14, icon, heading, status, hangup);
        box.setAlignment(Pos.CENTER);
        callOverlay.getChildren().add(box);
        callOverlay.setVisible(true);
    }


    private void startRingtone() {
        if (isRingtonePlaying) return;

        try {
            
            String ringtonePath = "/com/example/sounds/incoming_call_ringtone.mp3";
            javafx.scene.media.Media media = new javafx.scene.media.Media(
                    getClass().getResource(ringtonePath).toString()
            );

            ringtonePlayer = new javafx.scene.media.MediaPlayer(media);
            ringtonePlayer.setCycleCount(javafx.scene.media.MediaPlayer.INDEFINITE); // লুপ চালু
            ringtonePlayer.setVolume(0.7); 
            ringtonePlayer.play();

            isRingtonePlaying = true;
            System.out.println("[Ringtone] Started for incoming call");
        } catch (Exception e) {
            System.err.println("[Ringtone] Failed to play ringtone: " + e.getMessage());
        }
    }

    private void stopRingtone() {
        if (ringtonePlayer != null) {
            try {
                ringtonePlayer.stop();
                ringtonePlayer.dispose();
            } catch (Exception ignored) {}
            ringtonePlayer = null;
        }
        isRingtonePlaying = false;
        System.out.println("[Ringtone] Stopped");
    }
        private void showIncomingCallOverlay(String callId, String from, String callerNameOverride, boolean video) {
        if (callOverlay == null) return;
        if (callActive) {
            if (video) {
                SocketClient.sendPersistent("VIDEO_REJECT|" + callId + "|" + normEmail(currentUserEmail) + "|" + normEmail(from) + "|BUSY");
            } else {
                SocketClient.sendPersistent("CALL_REJECT|" + callId + "|" + normEmail(currentUserEmail) + "|" + normEmail(from) + "|BUSY");
            }
            return;
        }

        activeCallId = callId;
        callActive = true;
        videoCall = video;

        callOverlay.getChildren().clear();

        // Start ringtone when incoming call appears
        startRingtone();

        Label icon = new Label(video ? "📹" : "📞");
        icon.setFont(Font.font(32));

        String callerName = (callerNameOverride != null && !callerNameOverride.isBlank()
                && !callerNameOverride.equalsIgnoreCase(from))
                ? callerNameOverride
                : senderDisplayName(from);

        Label l = new Label("Incoming " + (video ? "video" : "audio") + " call from " + callerName);
        l.setTextFill(Color.WHITE);
        l.setFont(Font.font("DM Sans", 18));
        l.setWrapText(true);

        Label fromLabel = new Label(from);
        fromLabel.setTextFill(Color.web("#a0b9a5"));
        fromLabel.setFont(Font.font("DM Sans", 12));

        Label status = new Label("Ringing...");
        status.setTextFill(Color.web("#d8dee9"));
        status.setFont(Font.font("DM Sans", 13));
        callStatusLabel = status;

        Button accept = new Button("✓ Accept");
        accept.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 24; -fx-font-size: 14; -fx-cursor: hand;");
        accept.setOnAction(e -> {
            stopRingtone();                    // Ringtone বন্ধ
            if (currentUserEmail == null) return;
            showConnectingOverlay();
            new Thread(() -> {
                if (video) {
                    if (!ensureVideoPortReady()) {
                        Platform.runLater(() -> {
                            endActiveCallIfMatches(callId);
                            hideCallOverlay();
                            showAlert("Could not initialize camera/microphone for video.");
                        });
                        return;
                    }
                    SocketClient.sendPersistent("VIDEO_ACCEPT|" + callId + "|" + normEmail(currentUserEmail) + "|" + normEmail(from));
                } else {
                    if (!ensureAudioPortReady()) {
                        Platform.runLater(() -> {
                            endActiveCallIfMatches(callId);
                            hideCallOverlay();
                            showAlert("Could not initialize audio devices.");
                        });
                        return;
                    }
                    SocketClient.sendPersistent("CALL_ACCEPT|" + callId + "|" + normEmail(currentUserEmail) + "|" + normEmail(from));
                }
            }, video ? "video-accept-bind" : "audio-accept-bind").start();
        });

        Button reject = new Button("✕ Reject");
        reject.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 24; -fx-font-size: 14; -fx-cursor: hand;");
        reject.setOnAction(e -> {
            stopRingtone();                    // Ringtone বন্ধ
            if (currentUserEmail == null) return;
            if (video) {
                SocketClient.sendPersistent("VIDEO_REJECT|" + callId + "|" + normEmail(currentUserEmail) + "|" + normEmail(from) + "|REJECTED");
            } else {
                SocketClient.sendPersistent("CALL_REJECT|" + callId + "|" + normEmail(currentUserEmail) + "|" + normEmail(from) + "|REJECTED");
            }
            endActiveCallIfMatches(callId);
            hideCallOverlay();
        });

        HBox buttons = new HBox(16, accept, reject);
        buttons.setAlignment(Pos.CENTER);

        VBox box = new VBox(14, icon, l, fromLabel, status, buttons);
        box.setAlignment(Pos.CENTER);
        callOverlay.getChildren().add(box);
        callOverlay.setVisible(true);
    }

    
    private void showConnectingOverlay() {
        if (callOverlay == null) return;
        callOverlay.getChildren().clear();
        Label phoneIcon = new Label("📞");
        phoneIcon.setFont(Font.font(32));
        Label l = new Label("Connecting…");
        l.setTextFill(Color.WHITE);
        l.setFont(Font.font("DM Sans", 18));
        Label status = new Label("Setting up audio channel…");
        status.setTextFill(Color.web("#d8dee9"));
        status.setFont(Font.font("DM Sans", 13));
        callStatusLabel = status;
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(30, 30);
        spinner.setMaxSize(30, 30);
        VBox box = new VBox(14, phoneIcon, l, spinner, status);
        box.setAlignment(Pos.CENTER);
        callOverlay.getChildren().add(box);
        callOverlay.setVisible(true);
    }
    private void startAudioWithPeer(String callId, String peerIp, int peerPort) {
        if (activeCallId == null || !activeCallId.equals(callId)) return;
        // For video calls, keep the video overlay visible and do not override it.
        if (!videoCall) {
            // Already shows calling/connecting overlay. If we are here, update it to connecting.
            Platform.runLater(this::showConnectingOverlay);
        }
        try {
            audioCallService.connectToPeer(InetAddress.getByName(peerIp), peerPort);
        } catch (Exception e) {
            endActiveCallIfMatches(callId);
            Platform.runLater(() -> {
                hideCallOverlay();
                showAlert("Could not start audio.");
            });
            return;
        }
        // If no packets arrive after 5s, warn about firewall.
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                if (callActive && activeCallId != null && activeCallId.equals(callId)) {
                    long last = audioCallService.lastReceivedAt();
                    if (last == 0L) {
                        Platform.runLater(() -> showAlert(
                                "No incoming audio packets received. " +
                                        "This usually means UDP is blocked by Windows Firewall. " +
                                        "Allow UDP inbound/outbound for the Java app."
                        ));
                    }
                }
            } catch (InterruptedException ignored) {}
        }, "audio-received-check").start();
        Platform.runLater(() -> {
            if (videoCall) {
                // Video overlay is already shown by startVideoWithPeer; just start the UI monitor.
                startCallUiMonitor(callId, null);
                return;
            }
            if (callOverlay != null && activeCallId != null && activeCallId.equals(callId)) {
                callOverlay.getChildren().clear();
                Label phoneIcon = new Label("📞");
                phoneIcon.setFont(Font.font(32));
                Label l = new Label("In Call");
                l.setTextFill(Color.WHITE);
                l.setFont(Font.font("DM Sans", 20));
                Label durationLabel = new Label("00:00");
                durationLabel.setTextFill(Color.web("#2ecc71"));
                durationLabel.setFont(Font.font("DM Sans", 16));
                Label status = new Label("Audio active");
                status.setTextFill(Color.web("#d8dee9"));
                status.setFont(Font.font("DM Sans", 12));
                callStatusLabel = status;
                Button muteBtn = new Button("🎤 Mute");
                muteBtn.setStyle("-fx-background-color: #5e4a7a; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 20; -fx-font-size: 13; -fx-cursor: hand;");
                muteBtn.setOnAction(e -> {
                    boolean nowMuted = !audioCallService.isMuted();
                    audioCallService.setMuted(nowMuted);
                    muteBtn.setText(nowMuted ? "🔇 Unmute" : "🎤 Mute");
                    muteBtn.setStyle(nowMuted
                            ? "-fx-background-color: #e67e22; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 20; -fx-font-size: 13; -fx-cursor: hand;"
                            : "-fx-background-color: #5e4a7a; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 20; -fx-font-size: 13; -fx-cursor: hand;");
                });
                Button hangup = new Button("✕ Hang up");
                hangup.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 24; -fx-font-size: 14; -fx-cursor: hand;");
                hangup.setOnAction(e -> onAudioCallClick());
                HBox controls = new HBox(16, muteBtn, hangup);
                controls.setAlignment(Pos.CENTER);
                VBox box = new VBox(12, phoneIcon, l, durationLabel, status, controls);
                box.setAlignment(Pos.CENTER);
                callOverlay.getChildren().add(box);
                callOverlay.setVisible(true);
                startCallUiMonitor(callId, durationLabel);
            }
        });
    }
    private void startVideoWithPeer(String videoId, String peerIp, int peerPort) {
        if (activeCallId == null || !activeCallId.equals(videoId)) return;
        videoCall = true;
        Platform.runLater(this::showConnectingOverlay);
        try {
            videoCallService.start(InetAddress.getByName(peerIp), peerPort,
                    img -> Platform.runLater(() -> updateLocalVideoFrame(img)),
                    img -> Platform.runLater(() -> updateRemoteVideoFrame(img)));
        } catch (Exception e) {
            endActiveCallIfMatches(videoId);
            Platform.runLater(() -> {
                hideCallOverlay();
                showAlert("Could not start video stream.");
            });
            return;
        }
        Platform.runLater(this::showActiveVideoCallOverlay);
    }
    private void updateLocalVideoFrame(Image img) {
        if (localVideoView == null) return;
        localVideoView.setImage(img);
    }
    private void updateRemoteVideoFrame(Image img) {
        if (remoteVideoView == null) return;
        remoteVideoView.setImage(img);
    }
    private void showActiveVideoCallOverlay() {
        if (callOverlay == null) return;
        callOverlay.getChildren().clear();

        // ── Remote feed (fills all available space) ──────────────────────
        remoteVideoView = new ImageView();
        remoteVideoView.setPreserveRatio(true);
        remoteVideoView.setSmooth(true);
        remoteVideoView.setStyle("-fx-background-color: #0a0a0a;");
        // Bind remote view size to the callOverlay so it fills it regardless of window size
        remoteVideoView.fitWidthProperty().bind(callOverlay.widthProperty());
        remoteVideoView.fitHeightProperty().bind(callOverlay.heightProperty().subtract(64));

        // ── Local PiP (bottom-right corner) ──────────────────────────────
        localVideoView = new ImageView();
        localVideoView.setFitWidth(160);
        localVideoView.setFitHeight(120);
        localVideoView.setPreserveRatio(true);
        localVideoView.setSmooth(true);
        StackPane localPane = new StackPane(localVideoView);
        localPane.setMaxSize(160, 120);
        localPane.setStyle(
                "-fx-background-color: rgba(10,10,10,0.75);" +
                "-fx-background-radius: 10;" +
                "-fx-border-color: rgba(255,255,255,0.2);" +
                "-fx-border-radius: 10;" +
                "-fx-border-width: 1;" +
                "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.7),12,0,0,3);");
        StackPane.setAlignment(localPane, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(localPane, new Insets(0, 14, 70, 0));

        // ── Control bar ───────────────────────────────────────────────────
        // Mute button
        Button muteBtn = new Button("🎤  Mute");
        muteBtn.setStyle(
                "-fx-background-color: rgba(94,74,122,0.9);" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 22;" +
                "-fx-padding: 9 20;" +
                "-fx-font-size: 13px;" +
                "-fx-cursor: hand;");
        muteBtn.setOnAction(e -> {
            boolean nowMuted = !videoCallService.isMicMuted();
            videoCallService.setMicMuted(nowMuted);
            muteBtn.setText(nowMuted ? "🔇  Unmute" : "🎤  Mute");
            muteBtn.setStyle(nowMuted
                    ? "-fx-background-color: rgba(230,126,34,0.9); -fx-text-fill: white; -fx-background-radius: 22; -fx-padding: 9 20; -fx-font-size: 13px; -fx-cursor: hand;"
                    : "-fx-background-color: rgba(94,74,122,0.9); -fx-text-fill: white; -fx-background-radius: 22; -fx-padding: 9 20; -fx-font-size: 13px; -fx-cursor: hand;");
        });

        // Camera toggle button
        Button camBtn = new Button("📷  Cam Off");
        camBtn.setStyle(
                "-fx-background-color: rgba(94,74,122,0.9);" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 22;" +
                "-fx-padding: 9 20;" +
                "-fx-font-size: 13px;" +
                "-fx-cursor: hand;");
        camBtn.setOnAction(e -> {
            boolean nowOff = videoCallService.isCameraEnabled();
            videoCallService.setCameraEnabled(!nowOff);
            camBtn.setText(nowOff ? "📷  Cam On" : "📷  Cam Off");
            camBtn.setStyle(!nowOff
                    ? "-fx-background-color: rgba(230,126,34,0.9); -fx-text-fill: white; -fx-background-radius: 22; -fx-padding: 9 20; -fx-font-size: 13px; -fx-cursor: hand;"
                    : "-fx-background-color: rgba(94,74,122,0.9); -fx-text-fill: white; -fx-background-radius: 22; -fx-padding: 9 20; -fx-font-size: 13px; -fx-cursor: hand;");
        });

        // Hang-up button
        Button hangupBtn = new Button("✕  Hang Up");
        hangupBtn.setStyle(
                "-fx-background-color: rgba(231,76,60,0.9);" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 22;" +
                "-fx-padding: 9 24;" +
                "-fx-font-size: 13px;" +
                "-fx-cursor: hand;");
        hangupBtn.setOnAction(e -> {
            if (activeCallId != null) {
                SocketClient.sendPersistent("VIDEO_HANGUP|" + activeCallId
                        + "|" + normEmail(currentUserEmail)
                        + "|" + normEmail(selectedChatUserEmail));
            }
            endActiveCallIfMatches(activeCallId);
            hideCallOverlay();
        });

        HBox controls = new HBox(16, muteBtn, camBtn, hangupBtn);
        controls.setAlignment(Pos.CENTER);
        controls.setStyle(
                "-fx-background-color: rgba(15,10,30,0.82);" +
                "-fx-padding: 10 20 10 20;");
        controls.setPrefHeight(54);
        StackPane.setAlignment(controls, Pos.BOTTOM_CENTER);
        StackPane.setMargin(controls, new Insets(0));

        // ── Compose overlay ───────────────────────────────────────────────
        StackPane root = new StackPane(remoteVideoView, localPane, controls);
        root.setStyle("-fx-background-color: #0a0a0a;");
        root.prefWidthProperty().bind(callOverlay.widthProperty());
        root.prefHeightProperty().bind(callOverlay.heightProperty());

        callOverlay.getChildren().add(root);
        callOverlay.setVisible(true);
        startCallUiMonitor(activeCallId, null);
    }
    private void hideCallOverlay() {
        stopRingtone();
        if (callOverlay != null) {
            callOverlay.getChildren().clear();
            callOverlay.setVisible(false);
        }
    }
    private void endActiveCallIfMatches(String callId) {
        if (callId == null || activeCallId == null) return;
        if (!activeCallId.equals(callId)) return;
        stopRingtone();
        callActive = false;
        activeCallId = null;
        callUiMonitorRunning = false;
        // Cleanup active services
        audioCallService.stop();
        videoCallService.stop();
        videoCall = false;
        // Re-bind UDP ports (audio and video) for next call.
        if (currentUserEmail != null) {
            new Thread(() -> {
                try {
                    localAudioPort = audioCallService.bindPort();
                    if (SocketClient.isConnected()) {
                        SocketClient.sendPersistent("AUDIO_PORT|" + normEmail(currentUserEmail) + "|" + localAudioPort);
                    }
                } catch (Exception ignored) {
                    localAudioPort = -1;
                }
            }, "audio-rebind").start();
            new Thread(() -> {
                try {
                    localVideoPort = videoCallService.bindPort();
                    if (SocketClient.isConnected()) {
                        SocketClient.sendPersistent("VIDEO_PORT|" + normEmail(currentUserEmail) + "|" + localVideoPort);
                    }
                } catch (Exception ignored) {
                    localVideoPort = -1;
                }
            }, "video-rebind").start();
        }
    }
    /** Old signature for compatibility (ringing overlay, no duration label). */
    private void startCallUiMonitor(String callId) {
        startCallUiMonitor(callId, null);
    }
    private void startCallUiMonitor(String callId, Label durationLabel) {
        if (callUiMonitorRunning) return;
        callUiMonitorRunning = true;
        new Thread(() -> {
            while (callUiMonitorRunning && callActive && activeCallId != null && activeCallId.equals(callId)) {
                long duration = audioCallService.callDurationSeconds();
                long last = audioCallService.lastReceivedAt();
                // Format duration as mm:ss
                String durStr = String.format("%02d:%02d", duration / 60, duration % 60);
                String statusMsg;
                if (last == 0L) {
                    statusMsg = "Connecting audio…";
                } else {
                    long agoMs = Math.max(0L, System.currentTimeMillis() - last);
                    if (agoMs > 3000) {
                        statusMsg = "Audio interrupted (" + (agoMs / 1000) + "s ago)";
                    } else {
                        statusMsg = "Audio active";
                    }
                }
                Label label = callStatusLabel;
                Label durLabel = durationLabel;
                Platform.runLater(() -> {
                    if (callStatusLabel == label && callUiMonitorRunning && callActive) {
                        callStatusLabel.setText(statusMsg);
                    }
                    if (durLabel != null && callUiMonitorRunning && callActive) {
                        durLabel.setText(durStr);
                    }
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        }, "call-ui-monitor").start();
    }
    @FXML
    private void onInfoButtonClick() {
        if (isGroupChatActive && selectedGroupId != null) {
            openGroupProfile();
        } else if (selectedChatUserEmail != null)
            showAlert("User: " + selectedChatUserName + "\nEmail: " + selectedChatUserEmail);
        else
            showAlert("No active chat selected.");
    }
    private void hideAllPopups() {
        if (profileSettingsPopup != null) { profileSettingsPopup.hide(); profileSettingsPopup = null; }
        if (friendRequestsPopup != null) { friendRequestsPopup.hide(); friendRequestsPopup = null; }
      
        if (ProfileSettingsController.currentPopup != null) { ProfileSettingsController.currentPopup.hide(); ProfileSettingsController.currentPopup = null; }
        if (FriendRequestPopupController.currentPopup != null) { FriendRequestPopupController.currentPopup.hide(); FriendRequestPopupController.currentPopup = null; }
        if (MyProfileController.currentPopup != null) { MyProfileController.currentPopup.hide(); MyProfileController.currentPopup = null; }
        if (UpdateAccountController.currentPopup != null) { UpdateAccountController.currentPopup.hide(); UpdateAccountController.currentPopup = null; }
    }
    private void showPopupAt(Node anchorNode, Popup popup) {
        if (anchorNode == null || popup == null) return;
        Window owner = null;
        try {
            owner = anchorNode.getScene() != null ? anchorNode.getScene().getWindow() : null;
        } catch (Exception ignored) {}
        Bounds screenBounds = null;
        try {
            screenBounds = anchorNode.localToScreen(anchorNode.getBoundsInLocal());
        } catch (Exception ignored) {}
        double popupWidth = 280;
        Node content = popup.getContent().isEmpty() ? null : popup.getContent().get(0);
        if (content != null) {
            content.applyCss();
            if (content instanceof Parent) {
                ((Parent) content).layout();
            } else if (content instanceof Region) {
                ((Region) content).requestLayout();
            }
            double prefW = content.prefWidth(-1);
            if (prefW > 0) popupWidth = prefW;
        }
        if (popupWidth < 240) popupWidth = 240;
        if (popupWidth > 400) popupWidth = Math.min(popupWidth, 400);
        if (screenBounds != null) {
            double x = Math.max(8, screenBounds.getMaxX() - popupWidth - 8);
            double y = screenBounds.getMaxY() + 6;
            if (owner != null) popup.show(owner, x, y);
            else popup.show(anchorNode, x, y);
            return;
        }
        Stage stage = getChatOwnerStage();
        if (stage != null) {
            double x = stage.getX() + Math.max(0, stage.getWidth() - popupWidth - 8);
            double y = stage.getY() + 70;
            popup.show(stage, x, y);
            return;
        }
        // Final fallback without owner
        popup.show(anchorNode, 50, 70);
    }
    @FXML
    private void onProfileMenuClick() {
        if (profileSettingsPopup != null && profileSettingsPopup.isShowing()) {
            hideAllPopups();
            return;
        }
        hideAllPopups();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/ProfileSettings.fxml"));
            AnchorPane popupContent = loader.load();
            Popup popup = new Popup();
            popup.getContent().add(popupContent);
            popup.setAutoHide(true);
            profileSettingsPopup = popup;
            ProfileSettingsController.currentPopup = popup;
            showPopupAt(profileMenuBtn, popup);
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Could not open profile settings.");
        }
    }
    @FXML
    private void onFriendRequestsClick() {
        if (friendRequestsPopup != null && friendRequestsPopup.isShowing()) {
            hideAllPopups();
            return;
        }
        hideAllPopups();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/FriendRequestPopUp.fxml"));
            AnchorPane popupContent = loader.load();
            Popup popup = new Popup();
            popup.getContent().add(popupContent);
            popup.setAutoHide(true);
            friendRequestsPopup = popup;
            FriendRequestPopupController.currentPopup = popup;
            loadFriendRequestBadge();
            showPopupAt(friendRequestsBtn, popup);
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Could not open friend requests.");
        }
    }
    @FXML
    private void onNotificationsClick() {
        // Disabled; friends and messages are handled via list updates.
        showAlert("Notifications are disabled. Use friend request indicator and chat list.");
    }
    @FXML
    private void onGlobalSearchTyped() {
        if (globalSearch == null || currentUserEmail == null) return;
        String query = globalSearch.getText().trim();
        if (query.isEmpty()) {
            loadConversationList();
            return;
        }
        new Thread(() -> {
            String response = SocketClient.send("SEARCH_USER|" + query + "|" + currentUserEmail);
            Platform.runLater(() -> {
                if (response == null || !response.startsWith("SEARCH_RESULT|")) {
                    showErrorLabel("Search failed. Try again.");
                    return;
                }
                String data = response.substring("SEARCH_RESULT|".length());
                if (data.isBlank() || "EMPTY".equals(data)) {
                    showEmptyLabel("No users found.");
                    return;
                }
                List<String> users = new ArrayList<>();
                for (String token : data.split(",")) {
                    if (!token.isBlank()) users.add(token.trim());
                }
                renderUserList(users, false);
            });
        }).start();
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Image Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private void loadCurrentUserProfileImage(ImageView iv, int size) {
        if (iv == null) return;
        if (HomeController.currentProfilePic != null && !HomeController.currentProfilePic.isBlank()) {
            loadBase64Image(iv, HomeController.currentProfilePic, size);
            return;
        }
        loadDefaultProfileImage(iv, size);
    }
    private void loadProfileImage(ImageView iv, int size) {
        // Legacy method name compatibility for old call sites.
        loadDefaultProfileImage(iv, size);
    }
    private void loadDefaultProfileImage(ImageView iv, int size) {
        if (iv == null) return;
        try {
            Image img = new Image(
                    getClass().getResourceAsStream("/com/example/Image/default_avatar.png"));
            if (img != null && !img.isError()) {
                iv.setImage(img);
                return;
            }
        } catch (Exception ignored) {}
        fillSolidColor(iv, "#7B2CFF", size);
    }
    /**
     * Loads a user's profile picture into {@code imageView} asynchronously.
     * <ul>
     *   <li>Cache hit  → instant, no network</li>
     *   <li>Cache miss → shows default immediately, fetches GET_USER_INFO in background,
     *                    caches result, updates the view and the chat header if this user
     *                    is the currently open conversation.</li>
     * </ul>
     */
    private void loadProfilePictureForUser(String email, ImageView imageView, int size) {
        if (email == null || imageView == null) return;
        final String key = normEmail(email);

        // ── Cache hit ────────────────────────────────────────────────────────
        if (profilePictureCache.containsKey(key)) {
            imageView.setImage(profilePictureCache.get(key));
            if (key.equalsIgnoreCase(normEmail(selectedChatUserEmail)) && clientImage != null) {
                clientImage.setImage(profilePictureCache.get(key));
            }
            return;
        }

        loadDefaultProfileImage(imageView, size);   // placeholder while fetching

        new Thread(() -> {
            String response = SocketClient.send("GET_USER_INFO|" + key);
            // Server returns:  firstName|lastName|profilePicBase64  (no prefix).
            // Also handles legacy "USER_INFO|…" format just in case.
            if (response == null || response.equals("ERROR") || response.equals("CONNECTION_ERROR")) return;
            String raw = response.startsWith("USER_INFO|")
                    ? response.substring("USER_INFO|".length()) : response;
            String[] parts = raw.split("\\|", 3);
            String picBase64 = parts.length > 2 ? parts[2].trim() : "";
            if (picBase64.isBlank()) return;
            try {
                byte[] bytes = Base64.getDecoder().decode(picBase64);
                Image img = new Image(new java.io.ByteArrayInputStream(bytes));
                if (!img.isError()) {
                    profilePictureCache.put(key, img);
                    Platform.runLater(() -> {
                        imageView.setImage(img);
                        // Keep the chat-header avatar in sync.
                        if (key.equalsIgnoreCase(normEmail(selectedChatUserEmail))
                                && clientImage != null) {
                            clientImage.setImage(img);
                        }
                    });
                }
            } catch (Exception ignored) {}
        }, "load-avatar-" + key).start();
    }

    private void loadBase64Image(ImageView iv, String b64) {
        loadBase64Image(iv, b64, 40);
    }
    private void loadBase64Image(ImageView iv, String b64, int fallbackSize) {
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            Image img = new Image(new java.io.ByteArrayInputStream(bytes));
            if (!img.isError()) {
                iv.setImage(img);
                return;
            }
        } catch (Exception ignored) {}
        fillSolidColor(iv, "#7B2CFF", fallbackSize);
    }
    private void fillSolidColor(ImageView iv, String hex, int size) {
        WritableImage wi = new WritableImage(size, size);
        PixelWriter pw = wi.getPixelWriter();
        Color c = Color.web(hex);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                pw.setColor(x, y, c);
        iv.setImage(wi);
    }
    // ─────────────────────────────────────────────────────────────────────────
    // Search Functionality
    // ─────────────────────────────────────────────────────────────────────────
    @FXML
  /*  private void onSearchButtonClick() {
        if (searchMessages.isVisible()) {
            hideSearchField();
        } else {
            showSearchField();
        }
    }
    private void showSearchField() {
        if (searchMessages != null) {
            searchMessages.setVisible(true);
            searchMessages.requestFocus();
            searchMessages.textProperty().addListener((obs, oldText, newText) -> performSearch(newText));
        }
    }
    private void hideSearchField() {
        if (searchMessages != null) {
            searchMessages.setVisible(false);
            searchMessages.clear();
            clearSearchHighlights();
        }
    }
    private void performSearch(String query) {
    clearSearchHighlights();
    if (query == null || query.trim().isEmpty()) {
        return;
    }
    String searchTerm = query.trim().toLowerCase();
    int matchCount = 0;
    HBox firstMatch = null;
    for (MessageData msg : allMessages) {
        if (msg.text.toLowerCase().contains(searchTerm)) {
            highlightMessage(msg.container, true);
            matchCount++;
            if (firstMatch == null) {
                firstMatch = msg.container;
            }
        }
    }
    if (firstMatch != null) {
        scrollToMessage(firstMatch);
    }
   
    if (matchCount > 0) {
        System.out.println("[Search] " + matchCount + " messages found for: \"" + query + "\"");
    } else {
        System.out.println("[Search] No matches for: \"" + query + "\"");
    }
}
    private void highlightMessage(HBox container, boolean highlight) {
        if (highlight) {
            container.setStyle("-fx-background-color: rgba(255, 255, 0, 0.3); -fx-background-radius: 8;");
        } else {
            container.setStyle(""); // Remove highlight
        }
    }
    private void clearSearchHighlights() {
        for (MessageData msg : allMessages) {
            highlightMessage(msg.container, false);
        }
    }
    private void scrollToMessage(HBox messageContainer) {
        Platform.runLater(() -> {
            // Find the index of the message in msgbox
            int index = msgbox.getChildren().indexOf(messageContainer);
            if (index >= 0) {
                // Calculate scroll position (rough approximation)
                double scrollValue = (double) index / Math.max(1, allMessages.size() - 1);
                chatScroll.setVvalue(scrollValue);
            }
        });
    }*/
    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────
    private void scrollToBottom() {
        // Double-deferred: first runLater queues after the current FX pulse,
        // the nested one runs after the *next* layout pass so the ScrollPane
        // content bounds are fully updated before we set the position.
        Platform.runLater(() -> Platform.runLater(() -> chatScroll.setVvalue(1.0)));
    }
    private void showStatusLabel(String text, Color textColor) {
        friendslist.getChildren().clear();
        Label lbl = new Label(text);
        lbl.setTextFill(textColor);
        lbl.setFont(new Font("DM Sans", 13));
        lbl.setPadding(new Insets(20));
        friendslist.getChildren().add(lbl);
    }
    private void showLoadingLabel(String text) {
        showStatusLabel(text, Color.web("#a0b9a5"));
    }
    private void showErrorLabel(String text) {
        showStatusLabel(text, Color.web("#ff6b6b"));
    }
    private void showEmptyLabel(String text) {
        showStatusLabel(text, Color.web("#a0b9a5"));
    }
    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("PingOwl");
        a.setHeaderText(null);
        a.setGraphic(null);
        a.setContentText(msg);
        Stage owner = getChatOwnerStage();
        if (owner != null) {
            a.initModality(Modality.WINDOW_MODAL);
            a.initOwner(owner);
        }
        DialogPane pane = a.getDialogPane();
        pane.getStyleClass().add("create-group-dialog");
        var css = getClass().getResource("/com/example/chatStyle.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
        a.showAndWait();
    }
    /** Owner for modals so styling and stacking align with the chat window. */
    private Stage getChatOwnerStage() {
        if (createGroupBtn != null && createGroupBtn.getScene() != null
                && createGroupBtn.getScene().getWindow() instanceof Stage s) return s;
        if (msgField != null && msgField.getScene() != null
                && msgField.getScene().getWindow() instanceof Stage s) return s;
        if (leftbase != null && leftbase.getScene() != null
                && leftbase.getScene().getWindow() instanceof Stage s) return s;
        return null;
    }
}