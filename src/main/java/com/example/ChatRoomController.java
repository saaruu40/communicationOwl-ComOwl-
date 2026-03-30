package com.example;

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
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Cursor;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        startMessageUpdateThread();
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
            displayMessage(text, isMe, ts, "text", label);
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

    @FXML private void onSendImageClick()            { showAlert("Image sharing coming soon!"); }
    @FXML private void onVoiceToTextClick()          { showAlert("Voice-to-text coming soon!"); }
    @FXML private void onChangeDisplayPictureClick() { showAlert("Change profile picture coming soon!"); }

    @FXML
    private void onAudioCallClick() {
        if (selectedChatUserEmail == null) { showAlert("Select a user first."); return; }
        showAlert("Audio call with " + selectedChatUserName);
    }

    @FXML
    private void onVideoCallClick() {
        if (selectedChatUserEmail == null) { showAlert("Select a user first."); return; }
        showAlert("Video call with " + selectedChatUserName);
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