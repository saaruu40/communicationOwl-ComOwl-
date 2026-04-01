package com.example;

import com.codes.util.FileMessageCodec;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Cursor;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;

import java.net.InetAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    @FXML private Pane      leftbase;
    @FXML private HBox      userCard;
    @FXML private ImageView userImage;
    @FXML private Circle    userImageClip;
    @FXML private Label     userIdLabel;
    @FXML private TextField searchUsers;
    @FXML private ScrollPane leftpane;
    @FXML private VBox      friendslist;
    @FXML private Button    usersTabBtn;
    @FXML private Button    groupsTabBtn;

    // ── Main Chat ─────────────────────────────────────────────────────────────
    @FXML private ImageView  chatBG;
    @FXML private ScrollPane chatScroll;
    @FXML private VBox       msgbox;
    @FXML private VBox       chatbody;
    @FXML private StackPane  chatStateOverlay;
    @FXML private VBox       chatLoadingPane;
    @FXML private ProgressIndicator chatLoadingIndicator;
    @FXML private Label      chatLoadingLabel;
    @FXML private VBox       chatErrorPane;
    @FXML private Label      chatErrorLabel;
    @FXML private HBox       clientCard;
    @FXML private ImageView  clientImage;
    @FXML private Circle     clientImageClip;
    @FXML private Label      clientIdLabel;
    @FXML private StackPane  callOverlay;
    @FXML private VBox       groupProfilePane;

    private TextField   groupProfileNameField;
    private boolean     groupProfileShellBuilt;

    // ── Message Input ─────────────────────────────────────────────────────────
    @FXML private TextField msgField;
    @FXML private Button    sendMsg;
    @FXML private Button    sendImg;
    @FXML private Button    sendFile;
    @FXML private Button    voiceToText;
    @FXML private Button    sendEmoji;

    // ── Header Buttons ────────────────────────────────────────────────────────
    @FXML private HBox   headerButtonsBox;
    @FXML private Button audiocall;
    @FXML private Button videocall;
    @FXML private Button infobtn;
    @FXML private Button searchBtn;
    @FXML private TextField searchMessages;

    // ── Right Sidebar ─────────────────────────────────────────────────────────
    @FXML private Pane       geminiPane;
    @FXML private Pane       infoPane;
    @FXML private VBox       geminibox;
    @FXML private ScrollPane geminiscrollpane;
    @FXML private TextField  geminimsg;
    @FXML private Button     geminisend;
    @FXML private Button     createGroupBtn;
    @FXML private Label      USERNAME;
    @FXML private Button     geminiBTN;
    @FXML private Button     changeDOBtn;
    @FXML private ImageView  userImage1;
    @FXML private Circle     userImageClip1;

    // ── Emoji Picker ──────────────────────────────────────────────────────────
    @FXML private Pane       emojipane_test;
    @FXML private VBox       emojibox;
    @FXML private ScrollPane emojiscroller;
    @FXML private Button     closeEmojiPicker;

    // ── State ─────────────────────────────────────────────────────────────────
    private String currentUserEmail;
    private String currentUserName;

    private String  selectedChatUserEmail;
    private String  selectedChatUserName;
    private String  selectedGroupId;
    private String  selectedGroupName;
    private boolean isGroupChatActive  = false;
    private boolean showingUsersTab    = true;
    private String  lastMessageTimestamp = null;

    private final List<String> allUsers  = new ArrayList<>();
    private final List<String> allGroups = new ArrayList<>();
    private final List<MessageData> allMessages = new ArrayList<>();

    private volatile int latestUserRequestId = 0;
    /** Bumped when switching 1:1 or group chat so stale async responses are ignored. */
    private volatile int chatSessionId = 0;
    private Thread           messageUpdateThread;
    private volatile boolean isRunning = true;

    // ── Audio Call (1:1) ──────────────────────────────────────────────────────
    private final AudioCallService audioCallService = new AudioCallService();
    private volatile String activeCallId = null;
    private volatile String activeCallPeerEmail = null;
    private volatile boolean callActive = false;
    private volatile int localAudioPort = -1;
    private Consumer<String> signalingListener;
    private volatile boolean audioCallingInitialized = false;
    private volatile boolean callUiMonitorRunning = false;
    private volatile Label callStatusLabel;
    private final VideoCallService videoCallService = new VideoCallService();
    private volatile String activeVideoId = null;
    private volatile String activeVideoPeerEmail = null;
    private volatile boolean videoActive = false;
    private volatile int localVideoPort = -1;
    private ImageView localVideoView;
    private ImageView remoteVideoView;

    private static String normEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        initializeUI();
        loadCurrentUserInfo();
        loadAllUsers();
        loadGroupList();
        initializeEmojiPicker();
        initializeAudioCalling();
        startMessageUpdateThread();
    }

    private void initializeAudioCalling() {
        if (audioCallingInitialized) return;
        if (currentUserEmail == null) return;

        signalingListener = this::onSignalingEvent;
        SocketClient.addPersistentListener(signalingListener);
        audioCallingInitialized = true;

        // Bind a UDP port early (no mic/speaker opened yet) so CALL_ACCEPT can succeed.
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
        if (!line.startsWith("CALL_") && !line.startsWith("VIDEO_")) return;

        String[] parts = line.split("\\|");
        switch (parts[0]) {
            case "CALL_INVITE_OK" -> {
                // Server confirmed invite was delivered — nothing to do, wait for CALL_ESTABLISHED.
            }
            case "CALL_INVITE_OFFLINE" -> {
                Platform.runLater(() -> {
                    endActiveCallIfMatches(activeCallId);
                    hideCallOverlay();
                    showAlert("User is offline (or audio not registered yet).");
                });
            }
            case "CALL_INVITE_BUSY" -> {
                Platform.runLater(() -> {
                    endActiveCallIfMatches(activeCallId);
                    hideCallOverlay();
                    showAlert("User is busy on another call.");
                });
            }
            case "CALL_INVITE" -> {
                // CALL_INVITE|callId|from
                if (parts.length < 3) return;
                String callId = parts[1];
                String from = parts[2];
                if (currentUserEmail == null) return;
                if (Objects.equals(normEmail(from), normEmail(currentUserEmail))) return;

                Platform.runLater(() -> showIncomingCallOverlay(callId, from));
            }
            case "CALL_ESTABLISHED" -> {
                // CALL_ESTABLISHED|callId|peerIp|peerPort
                if (parts.length < 4) return;
                String callId = parts[1];
                String peerIp = parts[2];
                int peerPort;
                try { peerPort = Integer.parseInt(parts[3]); } catch (Exception e) { return; }

                Platform.runLater(() -> startAudioWithPeer(callId, peerIp, peerPort));
            }
            case "CALL_REJECTED" -> {
                // CALL_REJECTED|callId|from|reason
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
                    showAlert("Audio not ready on one of the devices. Try again.");
                });
            }
            case "CALL_ENDED" -> {
                // CALL_ENDED|callId|from
                String callId = parts.length > 1 ? parts[1] : null;
                Platform.runLater(() -> {
                    endActiveCallIfMatches(callId);
                    hideCallOverlay();
                    showAlert("Call ended.");
                });
            }
            case "VIDEO_INVITE_OFFLINE" -> Platform.runLater(() -> {
                endActiveVideoIfMatches(activeVideoId);
                showAlert("User is offline for video call.");
            });
            case "VIDEO_INVITE_BUSY" -> Platform.runLater(() -> {
                endActiveVideoIfMatches(activeVideoId);
                showAlert("User is busy on another video call.");
            });
            case "VIDEO_INVITE" -> {
                if (parts.length < 3) return;
                Platform.runLater(() -> showIncomingVideoCallOverlay(parts[1], parts[2]));
            }
            case "VIDEO_ESTABLISHED" -> {
                if (parts.length < 4) return;
                int peerPort;
                try { peerPort = Integer.parseInt(parts[3]); } catch (Exception e) { return; }
                String videoId = parts[1];
                String peerIp = parts[2];
                Platform.runLater(() -> startVideoWithPeer(videoId, peerIp, peerPort));
            }
            case "VIDEO_REJECTED" -> Platform.runLater(() -> {
                endActiveVideoIfMatches(activeVideoId);
                showAlert("Video call rejected.");
            });
            case "VIDEO_ACCEPT_NO_VIDEO_PORT" -> Platform.runLater(() -> {
                endActiveVideoIfMatches(activeVideoId);
                showAlert("Video is not ready on one of the devices.");
            });
            case "VIDEO_ENDED" -> {
                String videoId = parts.length > 1 ? parts[1] : null;
                Platform.runLater(() -> {
                    endActiveVideoIfMatches(videoId);
                    showAlert("Video call ended.");
                });
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI Setup
    // ─────────────────────────────────────────────────────────────────────────

    private void initializeUI() {
        chatScroll.setFitToWidth(true);
        msgbox.setSpacing(8);
        friendslist.setSpacing(0);

        // Live search filter
        if (searchUsers != null) {
            searchUsers.textProperty().addListener((obs, o, n) -> filterList(n));
        }

        styleTabBtn(usersTabBtn,  true);
        styleTabBtn(groupsTabBtn, false);
        updateCallControlsState();
    }

    /** Keep call buttons in sync with selected chat type and call state. */
    private void updateCallControlsState() {
        boolean oneToOneSelected = !isGroupChatActive
                && (selectedChatUserEmail != null || (callActive && activeCallPeerEmail != null));
        boolean canStartAudioCall = oneToOneSelected && !callActive;
        boolean canHangup = oneToOneSelected && callActive;

        if (audiocall != null) {
            audiocall.setDisable(!oneToOneSelected);
            audiocall.setText(canHangup ? "✕" : "☎");
            audiocall.setTooltip(new Tooltip(
                    canHangup ? "End current audio call"
                            : (canStartAudioCall ? "Start audio call" : "Audio call is available in 1:1 chat only")
            ));
        }
        if (videocall != null) {
            videocall.setDisable(!oneToOneSelected);
            videocall.setText(videoActive ? "✕" : "📹");
            videocall.setTooltip(new Tooltip(
                    oneToOneSelected ? (videoActive ? "End current video call" : "Start video call")
                            : "Video call is available in 1:1 chat only"
            ));
        }
    }

    private void styleTabBtn(Button btn, boolean active) {
        if (btn == null) return;
        btn.getStyleClass().removeAll("tab-btn-active", "tab-btn-inactive");
        btn.getStyleClass().add(active ? "tab-btn-active" : "tab-btn-inactive");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Current User
    // ─────────────────────────────────────────────────────────────────────────

    private void loadCurrentUserInfo() {
        if (currentUserEmail == null) currentUserEmail = HomeController.currentEmail;
        if (currentUserName  == null) currentUserName  = HomeController.currentFirstName;

        if (currentUserName != null) {
            if (userIdLabel != null) userIdLabel.setText(currentUserName);
            if (USERNAME    != null) USERNAME.setText(currentUserName);
        }

        loadProfileImage(userImage,  40);
        loadProfileImage(userImage1, 80);
    }

    public void setCurrentUserInfo(String email, String firstName) {
        this.currentUserEmail = email;
        this.currentUserName  = firstName;
        if (userIdLabel != null) userIdLabel.setText(firstName);
        if (USERNAME    != null) USERNAME.setText(firstName);

        // If controller ran initialize() before setCurrentUserInfo(), ensure calling is initialized now.
        if (!audioCallingInitialized && currentUserEmail != null) {
            initializeAudioCalling();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load Users
    // ─────────────────────────────────────────────────────────────────────────

    private void loadAllUsers() {
        if (currentUserEmail == null) return;

        int requestId = ++latestUserRequestId;
        showLoadingLabel("Loading users...");

        new Thread(() -> {
            String response = SocketClient.send("GET_ALL_USERS|" + currentUserEmail);
            Platform.runLater(() -> {
                if (requestId != latestUserRequestId) return;

                allUsers.clear();

                if (response == null || response.equals("CONNECTION_ERROR") || response.equals("ERROR")) {
                    showErrorLabel("Could not connect to server.");
                    return;
                }

                String data = response.startsWith("ALL_USERS|")
                        ? response.substring("ALL_USERS|".length()) : response;

                if (data.isBlank() || data.equals("EMPTY")) {
                    showEmptyLabel("No other users found.");
                    return;
                }

                for (String token : data.split(",")) {
                    if (!token.isBlank()) allUsers.add(token.trim());
                }

                renderUserList(allUsers);
            });
        }).start();
    }

    private void renderUserList(List<String> users) {
        friendslist.getChildren().clear();
        if (users.isEmpty()) { showEmptyLabel("No users found."); return; }
        for (String u : users) friendslist.getChildren().add(createUserItem(u));
    }

    private VBox createUserItem(String userData) {
        // Format: email:firstName:lastName:profilePicBase64
        String[] p       = userData.split(":", 4);
        String email     = p.length > 0 ? p[0] : "";
        String first     = p.length > 1 ? p[1] : "";
        String last      = p.length > 2 ? p[2] : "";
        String pic       = p.length > 3 ? p[3] : "";
        String display   = first.isBlank() ? email.split("@")[0] : (first + " " + last).trim();

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

        if (!pic.isBlank()) loadBase64Image(avatar, pic);
        else                loadProfileImage(avatar, 40);

        Label nameLabel = new Label(display);
        nameLabel.setTextFill(Color.web("#f3f4d2"));
        nameLabel.setFont(new Font("DM Sans", 14));

        Label emailLabel = new Label(email);
        emailLabel.setTextFill(Color.web("#a0b9a5"));
        emailLabel.setFont(new Font("DM Sans", 11));

        row.getChildren().addAll(avatar, new VBox(2, nameLabel, emailLabel));
        item.getChildren().add(row);

        item.setOnMouseClicked(e -> selectChat(email, display, avatar));
        item.setOnMouseEntered(e -> applyItemHover(item, true));
        item.setOnMouseExited(e  -> applyItemHover(item, false));

        return item;
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
            if (!showingUsersTab) filterList(searchUsers != null ? searchUsers.getText() : "");
            return;
        }

        String data = response.startsWith("MY_GROUPS|")
                ? response.substring("MY_GROUPS|".length()) : response;

        if (!data.isBlank() && !data.equals("EMPTY")) {
            for (String token : data.split(",")) {
                if (!token.isBlank()) allGroups.add(token.trim());
            }
        }

        if (!showingUsersTab) filterList(searchUsers != null ? searchUsers.getText() : "");
    }

    private void renderGroupList(List<String> groups) {
        friendslist.getChildren().clear();
        if (groups.isEmpty()) { showEmptyLabel("No groups yet. Create one!"); return; }
        for (String g : groups) friendslist.getChildren().add(createGroupItem(g));
    }

    private VBox createGroupItem(String groupData) {
        String[] p   = groupData.split(":", 2);
        String gId   = p.length > 0 ? p[0] : "";
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
        item.setOnMouseExited(e  -> applyItemHover(item, false));

        return item;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tab Switching
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void onUsersTabClick() {
        showingUsersTab = true;
        styleTabBtn(usersTabBtn,  true);
        styleTabBtn(groupsTabBtn, false);
        filterList(searchUsers != null ? searchUsers.getText() : "");
    }

    @FXML
    private void onGroupsTabClick() {
        showingUsersTab = false;
        styleTabBtn(usersTabBtn,  false);
        styleTabBtn(groupsTabBtn, true);
        showLoadingLabel("Loading groups…");
        loadGroupList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Search / Filter
    // ─────────────────────────────────────────────────────────────────────────

    private void filterList(String query) {
        if (showingUsersTab) {
            if (query == null || query.isBlank()) {
                // When no search query, show all locally loaded users
                renderUserList(allUsers);
                return;
            }

            int requestId = ++latestUserRequestId;
            showLoadingLabel("Searching users...");

            new Thread(() -> {
                String response = SocketClient.send("SEARCH_USER|" + query + "|" + currentUserEmail);
                Platform.runLater(() -> {
                    if (requestId != latestUserRequestId) return;

                    if (response == null || response.equals("CONNECTION_ERROR") || response.equals("ERROR")) {
                        String q = query.toLowerCase();
                        List<String> f = new ArrayList<>();
                        for (String u : allUsers) {
                            String[] p = u.split(":", 4);
                            String e1 = p.length > 0 ? p[0].toLowerCase() : "";
                            String e2 = p.length > 1 ? p[1].toLowerCase() : "";
                            String e3 = p.length > 2 ? p[2].toLowerCase() : "";
                            if (e1.contains(q) || e2.contains(q) || e3.contains(q)) f.add(u);
                        }
                        if (f.isEmpty())
                            showErrorLabel("Could not reach the server. No offline matches.");
                        else
                            renderUserList(f);
                        return;
                    }

                    String data = response.startsWith("SEARCH_RESULT|")
                            ? response.substring("SEARCH_RESULT|".length()) : response;

                    if (data.isBlank() || data.equals("EMPTY")) {
                        showEmptyLabel("No matching users found.");
                        return;
                    }

                    List<String> searchResults = new ArrayList<>();
                    for (String token : data.split(",")) {
                        if (!token.isBlank()) searchResults.add(token.trim());
                    }
                    renderUserList(searchResults);
                });
            }).start();

        } else {
            // Group search remains local (no server-side group search implemented yet)
            if (query == null || query.isBlank()) { renderGroupList(allGroups); return; }
            String q = query.toLowerCase();
            List<String> f = new ArrayList<>();
            for (String g : allGroups) {
                String[] p = g.split(":", 2);
                String n = p.length > 1 ? p[1].toLowerCase() : p[0].toLowerCase();
                if (n.contains(q)) f.add(g);
            }
            renderGroupList(f);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chat Selection
    // ─────────────────────────────────────────────────────────────────────────

    private void selectChat(String userEmail, String userName, ImageView avatarView) {
        chatSessionId++;
        final int session = chatSessionId;

        isGroupChatActive     = false;
        selectedChatUserEmail = userEmail;
        selectedChatUserName  = userName;
        selectedGroupId       = null;
        lastMessageTimestamp  = null;

        clientIdLabel.setText(userName);
        if (avatarView != null && avatarView.getImage() != null)
            clientImage.setImage(avatarView.getImage());

        msgbox.getChildren().clear();
        allMessages.clear(); // Clear stored messages for new chat
        msgField.clear();
        hideSearchField(); // Hide search when switching chats
        clearGroupHeaderHandlers();
        showGroupProfilePanel(false);
        updateCallControlsState();
        loadPrivateChatHistoryInitial(session);
    }

    private void selectGroupChat(String groupId, String groupName) {
        chatSessionId++;
        final int session = chatSessionId;

        isGroupChatActive     = true;
        selectedGroupId       = groupId;
        selectedGroupName     = groupName;
        selectedChatUserEmail = null;
        lastMessageTimestamp  = null;

        clientIdLabel.setText("👥 " + groupName);
        clientImage.setImage(null);

        msgbox.getChildren().clear();
        allMessages.clear(); // Clear stored messages for new chat
        msgField.clear();
        hideSearchField(); // Hide search when switching chats
        attachGroupHeaderHandlers();
        loadGroupHeaderFromServerAsync();
        updateCallControlsState();
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
        String pic  = sep >= 0 && sep + 1 < inner.length() ? inner.substring(sep + 1) : "";
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
            Label lbl = new Label(disp.isBlank() ? em : disp + "  ·  " + em);
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
            String last  = p.length > 2 ? p[2] : "";
            String disp  = (first + " " + last).trim();
            String label = disp.isBlank() ? email : disp + "  ·  " + email;
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

                applyPrivateHistoryFromResponse(response, false);
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
            String text   = parts[1];
            String ts     = parts.length > 2 ? parts[2] : "";
            String type   = parts.length > 3 ? parts[3] : "text";
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

                applyGroupHistoryFromResponse(response, false);
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
            String text   = parts[1];
            String ts     = parts.length > 2 ? parts[2] : "";
            boolean isMe  = sender.equals(currentUserEmail);
            String label  = isMe ? null : senderDisplayName(sender);
            String mtype  = FileMessageCodec.isFileMessage(text) ? "file" : "text";
            displayMessage(text, isMe, ts, mtype, label);
            lastMessageTimestamp = ts;
        }
    }

    private String senderDisplayName(String email) {
        for (String u : allUsers) {
            String[] p = u.split(":", 4);
            if (p.length > 0 && email.equalsIgnoreCase(p[0].trim())) {
                String first = p.length > 1 ? p[1] : "";
                String last  = p.length > 2 ? p[2] : "";
                String d     = (first + " " + last).trim();
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
                String last  = p.length > 2 ? p[2] : "";
                String d     = (first + " " + last).trim();
                return d.isBlank() ? email : d + "  ·  " + email;
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
        msgLabel.setFont(new Font("DM Sans", 13));
        msgLabel.getStyleClass().add(isMe ? "message-bubble-sent" : "message-bubble-received");

        Label tsLabel = new Label(timestamp);
        tsLabel.setFont(new Font("DM Sans", 10));
        tsLabel.setTextFill(Color.web("#aaaaaa"));

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
            who.setFont(new Font("DM Sans", 11));
            who.setTextFill(Color.web("#c9b8e8"));
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
            who.setFont(new Font("DM Sans", 11));
            who.setTextFill(Color.web("#c9b8e8"));
            bubble.getChildren().add(who);
        }

        Label clip = new Label("📎  " + parsed.filename());
        clip.setWrapText(true);
        clip.setFont(new Font("DM Sans", 13));
        clip.setTextFill(Color.WHITE);

        Label meta = new Label(parsed.mimeType());
        meta.setFont(new Font("DM Sans", 10));
        meta.setTextFill(Color.web("#bbbbbb"));

        Button saveBtn = new Button("Save as…");
        saveBtn.setStyle("-fx-background-color: #5e4a7a; -fx-text-fill: white; -fx-background-radius: 16; -fx-padding: 6 14; -fx-cursor: hand;");
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
        tsLabel.setFont(new Font("DM Sans", 10));
        tsLabel.setTextFill(Color.web("#aaaaaa"));

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
        else                   sendPrivateMessage(text);
    }

    private void sendPrivateMessage(String text) {
        if (selectedChatUserEmail == null) { showAlert("Please select a user to chat with."); return; }

        String cmd = "SEND_MESSAGE|" + currentUserEmail + "|" + selectedChatUserEmail + "|" + text;
        msgField.clear();

        new Thread(() -> {
            String response = SocketClient.send(cmd);
            Platform.runLater(() -> {
                if (response != null && response.startsWith("MESSAGE_SENT")) {
                    // Do not show an optimistic bubble here: the poll uses server timestamps, so the same
                    // line would appear again and look like a duplicate. Refresh from the server only.
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
        callUiMonitorRunning = false;
        if (signalingListener != null) SocketClient.removePersistentListener(signalingListener);
        hangupCurrentCall();
        hangupCurrentVideoCall();
        audioCallService.stop();
        videoCallService.stop();
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

    @FXML
    private void onGeminiSendClick() {
        String question = geminimsg.getText().trim();
        if (question.isEmpty()) return;

        appendGeminiMessage("You: " + question, "#4a7c59");
        geminimsg.clear();

        new Thread(() -> {
            String response = SocketClient.send("ASK_AI|" + question);
            String answer = (response != null && !response.equals("CONNECTION_ERROR"))
                    ? response : "Sorry, I could not reach the AI service.";
            Platform.runLater(() -> {
                appendGeminiMessage("AI: " + answer, "#5e4a7a");
                if (geminiscrollpane != null) geminiscrollpane.setVvalue(1.0);
            });
        }).start();
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
    private void onSendImageClick() {
        pickAndSendAttachment(true);
    }

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
    @FXML private void onVoiceToTextClick()          { showAlert("Voice-to-text coming soon!"); }
    @FXML private void onChangeDisplayPictureClick() { showAlert("Change profile picture coming soon!"); }

    @FXML
    private void onAudioCallClick() {
        if (!callActive && selectedChatUserEmail == null) { showAlert("Select a user first."); return; }
        if (isGroupChatActive) { showAlert("Audio calls are only available in 1-to-1 chat."); return; }
        if (currentUserEmail == null) { showAlert("Not logged in."); return; }
        if (!SocketClient.isConnected()) { showAlert("Not connected to server."); return; }

        if (callActive && activeCallId != null) {
            hangupCurrentCall();
            return;
        }

        // Ensure audio port is ready before initiating the call
        showCallingOverlay(selectedChatUserEmail);
        new Thread(() -> {
            if (!ensureAudioPortReady()) {
                Platform.runLater(() -> {
                    hideCallOverlay();
                    showAlert("Could not initialize audio (microphone/speaker). Check your audio devices.");
                });
                return;
            }

            String callId = currentUserEmail + "_" + System.currentTimeMillis();
            Platform.runLater(() -> {
                activeCallId = callId;
                activeCallPeerEmail = normEmail(selectedChatUserEmail);
                callActive = true;
                showCallingOverlay(selectedChatUserEmail);
                updateCallControlsState();
            });
            SocketClient.sendPersistent("CALL_INVITE|" + normEmail(currentUserEmail) + "|" + normEmail(selectedChatUserEmail) + "|" + callId);
        }, "audio-call-init").start();
    }

    @FXML
    private void onVideoCallClick() {
        if (!videoActive && selectedChatUserEmail == null) { showAlert("Select a user first."); return; }
        if (isGroupChatActive) { showAlert("Video calls are only available in 1-to-1 chat."); return; }
        if (currentUserEmail == null) { showAlert("Not logged in."); return; }
        if (!SocketClient.isConnected()) { showAlert("Not connected to server."); return; }

        if (videoActive && activeVideoId != null) {
            hangupCurrentVideoCall();
            return;
        }

        new Thread(() -> {
            if (!ensureVideoPortReady()) {
                Platform.runLater(() -> showAlert("Could not initialize video. Check webcam."));
                return;
            }

            String videoId = currentUserEmail + "_VIDEO_" + System.currentTimeMillis();
            Platform.runLater(() -> {
                activeVideoId = videoId;
                activeVideoPeerEmail = normEmail(selectedChatUserEmail);
                videoActive = true;
                showVideoCallingOverlay(selectedChatUserEmail);
            });
            SocketClient.sendPersistent("VIDEO_INVITE|" + normEmail(currentUserEmail) + "|" + normEmail(selectedChatUserEmail) + "|" + videoId);
        }, "video-call-init").start();
    }

    private void showCallingOverlay(String to) {
        if (callOverlay == null) return;
        callOverlay.getChildren().clear();

        // Phone icon
        Label phoneIcon = new Label("📞");
        phoneIcon.setFont(Font.font(32));

        String displayName = selectedChatUserName != null ? selectedChatUserName : senderDisplayName(to);
        Label l = new Label("Calling " + displayName + "…");
        l.setTextFill(Color.WHITE);
        l.setFont(Font.font("DM Sans", 18));
        Label status = new Label("Ringing…");
        status.setTextFill(Color.web("#d8dee9"));
        status.setFont(Font.font("DM Sans", 13));
        callStatusLabel = status;

        Button hangup = new Button("✕  Hang up");
        hangup.getStyleClass().add("call-btn");
        hangup.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 24; -fx-font-size: 14; -fx-cursor: hand;");
        hangup.setOnAction(e -> hangupCurrentCall());

        VBox box = new VBox(14, phoneIcon, l, status, hangup);
        box.setAlignment(Pos.CENTER);
        callOverlay.getChildren().add(box);
        callOverlay.setVisible(true);

        if (activeCallId != null) startCallUiMonitor(activeCallId);
    }

    private void showIncomingCallOverlay(String callId, String from) {
        if (callOverlay == null) return;
        if (callActive) {
            // Busy
            SocketClient.sendPersistent("CALL_REJECT|" + callId + "|" + normEmail(currentUserEmail) + "|" + normEmail(from) + "|BUSY");
            return;
        }

        activeCallId = callId;
        activeCallPeerEmail = normEmail(from);
        callActive = true;
        updateCallControlsState();

        callOverlay.getChildren().clear();

        // Caller profile preview (avatar + full name)
        ImageView callerAvatar = new ImageView();
        callerAvatar.setFitWidth(84);
        callerAvatar.setFitHeight(84);
        callerAvatar.setPreserveRatio(false);
        callerAvatar.setClip(new Circle(42, 42, 42));

        String callerName = senderDisplayName(from);
        String callerPicBase64 = null;
        for (String u : allUsers) {
            String[] p = u.split(":", 4);
            if (p.length > 0 && from.equalsIgnoreCase(p[0].trim())) {
                String first = p.length > 1 ? p[1].trim() : "";
                String last = p.length > 2 ? p[2].trim() : "";
                String full = (first + " " + last).trim();
                if (!full.isBlank()) callerName = full;
                callerPicBase64 = p.length > 3 ? p[3].trim() : "";
                break;
            }
        }
        if (callerPicBase64 != null && !callerPicBase64.isBlank()) {
            loadBase64Image(callerAvatar, callerPicBase64, 84);
        } else {
            loadProfileImage(callerAvatar, 84);
        }

        // Phone icon
        Label phoneIcon = new Label("📞");
        phoneIcon.setFont(Font.font(32));

        Label l = new Label("Incoming call from " + callerName);
        l.setTextFill(Color.WHITE);
        l.setFont(Font.font("DM Sans", 18));
        l.setWrapText(true);
        Label fromLabel = new Label(from);
        fromLabel.setTextFill(Color.web("#a0b9a5"));
        fromLabel.setFont(Font.font("DM Sans", 12));
        Label status = new Label("Incoming audio call…");
        status.setTextFill(Color.web("#d8dee9"));
        status.setFont(Font.font("DM Sans", 13));
        callStatusLabel = status;

        Button accept = new Button("✓  Accept");
        accept.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 24; -fx-font-size: 14; -fx-cursor: hand;");
        accept.setOnAction(e -> {
            if (currentUserEmail == null) return;
            showConnectingOverlay();
            // Ensure audio port is ready before accepting
            new Thread(() -> {
                if (!ensureAudioPortReady()) {
                    Platform.runLater(() -> {
                        endActiveCallIfMatches(callId);
                        hideCallOverlay();
                        updateCallControlsState();
                        showAlert("Could not initialize audio devices.");
                    });
                    return;
                }
                SocketClient.sendPersistent("CALL_ACCEPT|" + callId + "|" + normEmail(currentUserEmail) + "|" + normEmail(from));
            }, "audio-accept-bind").start();
        });

        Button reject = new Button("✕  Reject");
        reject.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 24; -fx-font-size: 14; -fx-cursor: hand;");
        reject.setOnAction(e -> {
            if (currentUserEmail == null) return;
            SocketClient.sendPersistent("CALL_REJECT|" + callId + "|" + normEmail(currentUserEmail) + "|" + normEmail(from) + "|REJECTED");
            endActiveCallIfMatches(callId);
            hideCallOverlay();
            updateCallControlsState();
        });

        HBox buttons = new HBox(16, accept, reject);
        buttons.setAlignment(Pos.CENTER);
        VBox box = new VBox(14, callerAvatar, phoneIcon, l, fromLabel, status, buttons);
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
        Button cancelHangup = new Button("✕  Cancel");
        cancelHangup.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 24; -fx-font-size: 14; -fx-cursor: hand;");
        cancelHangup.setOnAction(e -> hangupCurrentCall());
        VBox box = new VBox(14, phoneIcon, l, spinner, status, cancelHangup);
        box.setAlignment(Pos.CENTER);
        callOverlay.getChildren().add(box);
        callOverlay.setVisible(true);
    }

    private void startAudioWithPeer(String callId, String peerIp, int peerPort) {
        if (activeCallId == null || !activeCallId.equals(callId)) return;
        try {
            audioCallService.connectToPeer(InetAddress.getByName(peerIp), peerPort);
        } catch (Exception e) {
            endActiveCallIfMatches(callId);
            hideCallOverlay();
            showAlert("Could not start audio.");
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

        if (callOverlay != null) {
            callOverlay.getChildren().clear();

            Label phoneIcon = new Label("📞");
            phoneIcon.setFont(Font.font(32));

            Label l = new Label("In Call");
            l.setTextFill(Color.WHITE);
            l.setFont(Font.font("DM Sans", 20));

            Label durationLabel = new Label("00:00");
            durationLabel.setTextFill(Color.web("#2ecc71"));
            durationLabel.setFont(Font.font("DM Sans", 16));

            Label status = new Label("Waiting for voice…");
            status.setTextFill(Color.web("#d8dee9"));
            status.setFont(Font.font("DM Sans", 12));
            callStatusLabel = status;

            // Mute button
            Button muteBtn = new Button("🎤  Mute");
            muteBtn.setStyle("-fx-background-color: #5e4a7a; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 20; -fx-font-size: 13; -fx-cursor: hand;");
            muteBtn.setOnAction(e -> {
                boolean nowMuted = !audioCallService.isMuted();
                audioCallService.setMuted(nowMuted);
                muteBtn.setText(nowMuted ? "🔇  Unmute" : "🎤  Mute");
                muteBtn.setStyle(nowMuted
                    ? "-fx-background-color: #e67e22; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 20; -fx-font-size: 13; -fx-cursor: hand;"
                    : "-fx-background-color: #5e4a7a; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 20; -fx-font-size: 13; -fx-cursor: hand;");
            });

            // Hang up button
            Button hangup = new Button("✕  Hang up");
            hangup.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 24; -fx-font-size: 14; -fx-cursor: hand;");
            hangup.setOnAction(e -> hangupCurrentCall());

            HBox controls = new HBox(16, muteBtn, hangup);
            controls.setAlignment(Pos.CENTER);

            VBox box = new VBox(12, phoneIcon, l, durationLabel, status, controls);
            box.setAlignment(Pos.CENTER);
            callOverlay.getChildren().add(box);
            callOverlay.setVisible(true);

            // Start the UI monitor that updates duration + status text
            startCallUiMonitor(callId, durationLabel);
        }
    }

    private void hideCallOverlay() {
        if (callOverlay != null) {
            callOverlay.getChildren().clear();
            callOverlay.setVisible(false);
        }
    }

    private void showIncomingVideoCallOverlay(String videoId, String from) {
        if (videoActive) {
            SocketClient.sendPersistent("VIDEO_REJECT|" + videoId + "|" + normEmail(currentUserEmail) + "|" + normEmail(from) + "|BUSY");
            return;
        }
        activeVideoId = videoId;
        activeVideoPeerEmail = normEmail(from);
        videoActive = true;
        if (callOverlay == null) return;
        callOverlay.getChildren().clear();

        Label camIcon = new Label("📹");
        camIcon.setFont(Font.font(32));
        Label title = new Label("Incoming video call");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font("DM Sans", 20));
        Label who = new Label("From: " + senderDisplayName(from));
        who.setTextFill(Color.web("#f3f4d2"));
        who.setFont(Font.font("DM Sans", 16));
        Label fromLabel = new Label(from);
        fromLabel.setTextFill(Color.web("#a0b9a5"));
        String selfName = currentUserName != null ? currentUserName : (currentUserEmail != null ? normEmail(currentUserEmail) : "You");
        Label youLine = new Label("You: " + selfName);
        youLine.setTextFill(Color.web("#d8dee9"));
        youLine.setFont(Font.font("DM Sans", 13));

        Button accept = new Button("✓  Accept");
        accept.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 24; -fx-font-size: 14; -fx-cursor: hand;");
        accept.setOnAction(e -> {
            showVideoConnectingOverlay();
            new Thread(() -> {
                if (!ensureVideoPortReady()) {
                    Platform.runLater(() -> {
                        endActiveVideoIfMatches(videoId);
                        hideCallOverlay();
                        showAlert("Could not initialize video.");
                    });
                    return;
                }
                SocketClient.sendPersistent("VIDEO_ACCEPT|" + videoId + "|" + normEmail(currentUserEmail) + "|" + normEmail(from));
            }, "video-accept-bind").start();
        });

        Button reject = new Button("✕  Reject");
        reject.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 24; -fx-font-size: 14; -fx-cursor: hand;");
        reject.setOnAction(e -> {
            SocketClient.sendPersistent("VIDEO_REJECT|" + videoId + "|" + normEmail(currentUserEmail) + "|" + normEmail(from) + "|REJECTED");
            endActiveVideoIfMatches(videoId);
            hideCallOverlay();
        });

        HBox actions = new HBox(16, accept, reject);
        actions.setAlignment(Pos.CENTER);
        VBox box = new VBox(14, camIcon, title, who, fromLabel, youLine, actions);
        box.setAlignment(Pos.CENTER);
        callOverlay.getChildren().add(box);
        callOverlay.setVisible(true);
    }

    private void startVideoWithPeer(String videoId, String peerIp, int peerPort) {
        if (activeVideoId == null || !activeVideoId.equals(videoId)) return;
        showVideoInCallOverlay();
        try {
            videoCallService.start(InetAddress.getByName(peerIp), peerPort,
                    img -> Platform.runLater(() -> { if (localVideoView != null) localVideoView.setImage(img); }),
                    img -> Platform.runLater(() -> { if (remoteVideoView != null) remoteVideoView.setImage(img); }));
        } catch (Exception e) {
            endActiveVideoIfMatches(videoId);
            showAlert("Could not start video call.");
        }
    }

    private void showVideoCallingOverlay(String to) {
        if (callOverlay == null) return;
        callOverlay.getChildren().clear();

        String peerDisplay = senderDisplayName(to);
        String selfDisplay = currentUserName != null && !currentUserName.isBlank()
                ? currentUserName
                : (currentUserEmail != null ? normEmail(currentUserEmail) : "You");

        StackPane remoteWrap = new StackPane();
        remoteWrap.setPrefSize(640, 400);
        remoteWrap.setStyle("-fx-background-color: black;");
        VBox ringCol = new VBox(8);
        ringCol.setAlignment(Pos.CENTER);
        Label camIcon = new Label("📹");
        camIcon.setFont(Font.font(32));
        Label title = new Label("Calling " + peerDisplay + "…");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font("DM Sans", 18));
        Label status = new Label("Ringing…");
        status.setTextFill(Color.web("#d8dee9"));
        ringCol.getChildren().addAll(camIcon, title, status);
        remoteWrap.getChildren().add(ringCol);

        Label remoteHint = new Label("Remote · " + peerDisplay);
        remoteHint.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-text-fill: white; -fx-padding: 6 12; -fx-font-size: 12;");
        StackPane.setAlignment(remoteHint, Pos.TOP_LEFT);
        StackPane.setMargin(remoteHint, new Insets(10, 12, 0, 0));
        remoteWrap.getChildren().add(remoteHint);

        localVideoView = new ImageView();
        localVideoView.setFitWidth(176);
        localVideoView.setFitHeight(120);
        localVideoView.setPreserveRatio(true);
        Label localTag = new Label("You · " + selfDisplay);
        localTag.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-text-fill: #f3f4d2; -fx-padding: 4 10; -fx-font-size: 12;");
        StackPane localPipStack = new StackPane(localVideoView, localTag);
        StackPane.setAlignment(localTag, Pos.TOP_LEFT);
        StackPane localPipOuter = new StackPane(localPipStack);
        localPipOuter.setPrefSize(176, 120);
        localPipOuter.setMaxSize(176, 120);
        localPipOuter.setStyle("-fx-background-color: #111; -fx-border-color: #444; -fx-border-width: 2;");
        StackPane.setAlignment(localPipOuter, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(localPipOuter, new Insets(0, 16, 72, 0));

        Button camBtn = new Button(videoCallService.isCameraEnabled() ? "📷  Camera off" : "📹  Camera on");
        camBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 16; -fx-font-size: 13; -fx-cursor: hand;");
        camBtn.setOnAction(e -> {
            boolean on = !videoCallService.isCameraEnabled();
            videoCallService.setCameraEnabled(on);
            camBtn.setText(on ? "📷  Camera off" : "📹  Camera on");
        });
        Button hangup = new Button("✕  Cancel");
        hangup.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 24; -fx-font-size: 14; -fx-cursor: hand;");
        hangup.setOnAction(e -> hangupCurrentVideoCall());
        HBox ringControls = new HBox(12, camBtn, hangup);
        ringControls.setAlignment(Pos.CENTER);
        StackPane.setAlignment(ringControls, Pos.BOTTOM_CENTER);
        StackPane.setMargin(ringControls, new Insets(0, 0, 12, 0));

        StackPane rootPane = new StackPane(remoteWrap, localPipOuter, ringControls);
        callOverlay.getChildren().add(rootPane);
        callOverlay.setVisible(true);

        new Thread(() -> {
            try {
                videoCallService.startLocalPreview(img -> Platform.runLater(() -> {
                    if (localVideoView != null) localVideoView.setImage(img);
                }));
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Could not initialize camera."));
            }
        }, "video-ring-preview").start();
    }

    private void showVideoConnectingOverlay() {
        if (callOverlay == null) return;
        callOverlay.getChildren().clear();

        String selfDisplay = currentUserName != null && !currentUserName.isBlank()
                ? currentUserName
                : (currentUserEmail != null ? normEmail(currentUserEmail) : "You");

        StackPane remoteWrap = new StackPane();
        remoteWrap.setPrefSize(640, 400);
        remoteWrap.setStyle("-fx-background-color: black;");
        VBox centerCol = new VBox(10);
        centerCol.setAlignment(Pos.CENTER);
        Label camIcon = new Label("📹");
        camIcon.setFont(Font.font(32));
        Label title = new Label("Connecting video…");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font("DM Sans", 18));
        ProgressIndicator pi = new ProgressIndicator();
        pi.setPrefSize(30, 30);
        centerCol.getChildren().addAll(camIcon, title, pi);
        remoteWrap.getChildren().add(centerCol);

        localVideoView = new ImageView();
        localVideoView.setFitWidth(176);
        localVideoView.setFitHeight(120);
        localVideoView.setPreserveRatio(true);
        Label localTag = new Label("You · " + selfDisplay);
        localTag.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-text-fill: #f3f4d2; -fx-padding: 4 10; -fx-font-size: 12;");
        StackPane localPipStack = new StackPane(localVideoView, localTag);
        StackPane.setAlignment(localTag, Pos.TOP_LEFT);
        StackPane localPipOuter = new StackPane(localPipStack);
        localPipOuter.setPrefSize(176, 120);
        localPipOuter.setMaxSize(176, 120);
        localPipOuter.setStyle("-fx-background-color: #111; -fx-border-color: #444; -fx-border-width: 2;");
        StackPane.setAlignment(localPipOuter, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(localPipOuter, new Insets(0, 16, 72, 0));

        Button camBtn = new Button(videoCallService.isCameraEnabled() ? "📷  Camera off" : "📹  Camera on");
        camBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 16; -fx-font-size: 13; -fx-cursor: hand;");
        camBtn.setOnAction(e -> {
            boolean on = !videoCallService.isCameraEnabled();
            videoCallService.setCameraEnabled(on);
            camBtn.setText(on ? "📷  Camera off" : "📹  Camera on");
        });
        Button endBtn = new Button("✕  Cancel");
        endBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 20; -fx-font-size: 14;");
        endBtn.setOnAction(e -> hangupCurrentVideoCall());
        HBox connectControls = new HBox(12, camBtn, endBtn);
        connectControls.setAlignment(Pos.CENTER);
        StackPane.setAlignment(connectControls, Pos.BOTTOM_CENTER);
        StackPane.setMargin(connectControls, new Insets(0, 0, 12, 0));

        StackPane rootPane = new StackPane(remoteWrap, localPipOuter, connectControls);
        callOverlay.getChildren().add(rootPane);
        callOverlay.setVisible(true);

        new Thread(() -> {
            try {
                videoCallService.startLocalPreview(img -> Platform.runLater(() -> {
                    if (localVideoView != null) localVideoView.setImage(img);
                }));
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Could not initialize camera."));
            }
        }, "video-connect-preview").start();
    }

    private void showVideoInCallOverlay() {
        if (callOverlay == null) return;
        callOverlay.getChildren().clear();

        String peerDisplay = activeVideoPeerEmail != null ? senderDisplayName(activeVideoPeerEmail) : "Contact";
        String selfDisplay = currentUserName != null && !currentUserName.isBlank()
                ? currentUserName
                : (currentUserEmail != null ? normEmail(currentUserEmail) : "You");

        remoteVideoView = new ImageView();
        remoteVideoView.setFitWidth(640);
        remoteVideoView.setFitHeight(400);
        remoteVideoView.setPreserveRatio(true);

        localVideoView = new ImageView();
        localVideoView.setFitWidth(176);
        localVideoView.setFitHeight(120);
        localVideoView.setPreserveRatio(true);

        Label remoteTag = new Label("Remote · " + peerDisplay);
        remoteTag.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-text-fill: white; -fx-padding: 6 12; -fx-font-size: 13;");
        StackPane remoteWrap = new StackPane(remoteVideoView, remoteTag);
        remoteWrap.setPrefSize(640, 400);
        remoteWrap.setStyle("-fx-background-color: black;");
        StackPane.setAlignment(remoteTag, Pos.TOP_LEFT);
        StackPane.setMargin(remoteTag, new Insets(10, 12, 0, 0));

        Label localTag = new Label("You · " + selfDisplay);
        localTag.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-text-fill: #f3f4d2; -fx-padding: 4 10; -fx-font-size: 12;");
        StackPane localPipStack = new StackPane(localVideoView, localTag);
        StackPane.setAlignment(localTag, Pos.TOP_LEFT);

        StackPane localPipOuter = new StackPane(localPipStack);
        localPipOuter.setPrefSize(176, 120);
        localPipOuter.setMaxSize(176, 120);
        localPipOuter.setStyle("-fx-background-color: #111; -fx-border-color: #444; -fx-border-width: 2;");
        StackPane.setAlignment(localPipOuter, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(localPipOuter, new Insets(0, 16, 72, 0));

        Button muteBtn = new Button("🎤  Mute");
        muteBtn.setStyle("-fx-background-color: #5e4a7a; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 16; -fx-font-size: 13; -fx-cursor: hand;");
        muteBtn.setOnAction(e -> {
            boolean nowMuted = !videoCallService.isMicMuted();
            videoCallService.setMicMuted(nowMuted);
            muteBtn.setText(nowMuted ? "🔇  Unmute" : "🎤  Mute");
            muteBtn.setStyle(nowMuted
                    ? "-fx-background-color: #e67e22; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 16; -fx-font-size: 13; -fx-cursor: hand;"
                    : "-fx-background-color: #5e4a7a; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 16; -fx-font-size: 13; -fx-cursor: hand;");
        });

        Button cameraBtn = new Button(videoCallService.isCameraEnabled() ? "📷  Camera off" : "📹  Camera on");
        cameraBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 16; -fx-font-size: 13; -fx-cursor: hand;");
        cameraBtn.setOnAction(e -> {
            boolean on = !videoCallService.isCameraEnabled();
            videoCallService.setCameraEnabled(on);
            cameraBtn.setText(on ? "📷  Camera off" : "📹  Camera on");
        });

        Button endBtn = new Button("✕  Hang up");
        endBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 8 20; -fx-font-size: 14; -fx-cursor: hand;");
        endBtn.setOnAction(e -> hangupCurrentVideoCall());

        HBox videoControls = new HBox(12, muteBtn, cameraBtn, endBtn);
        videoControls.setAlignment(Pos.CENTER);
        StackPane.setAlignment(videoControls, Pos.BOTTOM_CENTER);
        StackPane.setMargin(videoControls, new Insets(0, 0, 12, 0));

        StackPane rootPane = new StackPane(remoteWrap, localPipOuter, videoControls);
        callOverlay.getChildren().add(rootPane);
        callOverlay.setVisible(true);
    }

    private void hangupCurrentVideoCall() {
        if (!videoActive || activeVideoId == null) return;
        if (currentUserEmail != null && SocketClient.isConnected() && activeVideoPeerEmail != null) {
            SocketClient.sendPersistent("VIDEO_HANGUP|" + activeVideoId + "|"
                    + normEmail(currentUserEmail) + "|" + normEmail(activeVideoPeerEmail));
        }
        endActiveVideoIfMatches(activeVideoId);
    }

    private void endActiveVideoIfMatches(String videoId) {
        if (videoId == null || activeVideoId == null) return;
        if (!activeVideoId.equals(videoId)) return;
        videoActive = false;
        activeVideoId = null;
        activeVideoPeerEmail = null;
        videoCallService.stop();
        localVideoView = null;
        remoteVideoView = null;
        hideCallOverlay();
        updateCallControlsState();

        if (currentUserEmail != null) {
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

    private void hangupCurrentCall() {
        if (!callActive || activeCallId == null) return;
        if (currentUserEmail != null && SocketClient.isConnected() && activeCallPeerEmail != null) {
            SocketClient.sendPersistent("CALL_HANGUP|" + activeCallId + "|"
                    + normEmail(currentUserEmail) + "|" + normEmail(activeCallPeerEmail));
        }
        endActiveCallIfMatches(activeCallId);
        hideCallOverlay();
        updateCallControlsState();
    }

    private void endActiveCallIfMatches(String callId) {
        if (callId == null || activeCallId == null) return;
        if (!activeCallId.equals(callId)) return;
        callActive = false;
        activeCallId = null;
        activeCallPeerEmail = null;
        callUiMonitorRunning = false;
        audioCallService.stop();
        updateCallControlsState();

        // Re-bind UDP port only (no mic/speaker) for next call
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

    // ─────────────────────────────────────────────────────────────────────────
    // Image Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void loadProfileImage(ImageView iv, int size) {
        if (iv == null) return;
        try {
            Image img = new Image(
                    getClass().getResourceAsStream("/com/example/Image/default-avatar.png"));
            if (img != null && !img.isError()) { iv.setImage(img); return; }
        } catch (Exception ignored) {}
        fillSolidColor(iv, "#7B2CFF", size);
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
        PixelWriter pw   = wi.getPixelWriter();
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
    private void onSearchButtonClick() {
        if (searchMessages.isVisible()) {
            hideSearchField();
        } else {
            showSearchField();
        }
    }

    private void showSearchField() {
        if (searchMessages != null) {
            searchMessages.setManaged(true);
            searchMessages.setVisible(true);
            searchMessages.requestFocus();
            searchMessages.textProperty().addListener((obs, oldText, newText) -> performSearch(newText));
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
        boolean firstMatch = true;

        for (MessageData msg : allMessages) {
            if (msg.text.toLowerCase().contains(searchTerm)) {
                highlightMessage(msg.container, true);

                // Scroll to first match
                if (firstMatch) {
                    scrollToMessage(msg.container);
                    firstMatch = false;
                }
            }
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
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private void scrollToBottom() {
        Platform.runLater(() -> chatScroll.setVvalue(1.0));
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