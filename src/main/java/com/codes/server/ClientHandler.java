package com.codes.server;

import com.codes.auth.AuthenticationService;
import com.codes.model.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final AuthenticationService authService;
    private String persistentEmail = null;
    private static String normEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.authService = new AuthenticationService();
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new java.io.OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                System.out.println("Received from client: " + line);

                String response = processCommand(line, out);
                if (response != null) {
                    for (String responseLine : response.split("\n")) {
                        out.println(responseLine);
                    }
                }
                out.println("END_OF_RESPONSE");
            }

        } catch (IOException e) {
            System.out.println("Client disconnected");
        } finally {
            if (persistentEmail != null) {
                OnlineUserManager.removeUser(persistentEmail);
            }
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String processCommand(String command, PrintWriter out) {
        String[] parts = command.split("\\|");

        System.out.println("[Server] processCommand command='" + command + "' parts0='" + (parts.length>0 ? parts[0] : "<empty>") + "'");

        try {
            if (parts[0].equalsIgnoreCase("SIGNUP")) {

                boolean success = authService.signUp(
                        parts[1], parts[2], parts[3],
                        parts[4], parts[5], parts[6], parts[7],
                        parts.length > 8 ? parts[8] : ""
                );

                return success
                        ? "SIGNUP_SUCCESS"
                        : "SIGNUP_FAILED|Email already exists or passwords don't match";

            } else if (parts[0].equalsIgnoreCase("LOGIN")) {
                String[] loginParts = command.split("\\|", 3);
                if (loginParts.length < 3) return "LOGIN_FAILED";
                User user = authService.login(loginParts[1], loginParts[2]);

                return (user != null)
                        ? "LOGIN_SUCCESS|" + user.getFirstName()
                        : "LOGIN_FAILED";

            } else if (parts[0].equalsIgnoreCase("FORGOT_QUESTION")) {

                String question = authService.getRecoveryQuestion(parts[1]);

                return (question != null)
                        ? "QUESTION|" + question
                        : "EMAIL_NOT_FOUND";

            } else if (parts[0].equalsIgnoreCase("GET_USER_INFO") || parts[0].equalsIgnoreCase("GET_PROFILE")) {
                if (parts.length < 2) return "ERROR";
                String profile = authService.getUserProfile(parts[1]);
                return profile != null ? profile : "ERROR";

            } else if (parts[0].equalsIgnoreCase("CHECK_ANSWER")) {
                boolean success = authService.checkRecoveryAnswer(parts[1], parts[2]);
                return success ? "ANSWER_CORRECT" : "ANSWER_WRONG";
            } else if (parts[0].equalsIgnoreCase("RESET_PASSWORD")) {

                boolean success = authService.resetPasswordByQuestion(
                        parts[1], parts[2], parts[3], parts[4]
                );

                return success
                        ? "RESET_SUCCESS"
                        : "RESET_FAILED|Wrong answer or passwords don't match";
            }else if (parts[0].equalsIgnoreCase("SEND_REQUEST")) {
                String senderEmail = normEmail(parts[1]);
                String receiverEmail = normEmail(parts[2]);
                String result = authService.sendFriendRequest(senderEmail, receiverEmail);

                if ("REQUEST_SENT".equals(result)) {
                    String senderProfile = authService.getUserProfile(senderEmail);
                    String senderFirstName = senderEmail;
                    if (senderProfile != null && !senderProfile.equals("ERROR")) {
                        String[] profileParts = senderProfile.split("\\|", 2);
                        if (profileParts.length > 0 && !profileParts[0].isBlank()) {
                            senderFirstName = profileParts[0];
                        }
                    }
                    OnlineUserManager.sendToUser(receiverEmail,
                            "NOTIFICATION|FRIEND_REQUEST|" + senderEmail + "|" + senderFirstName);
                }
                return result;

            } else if (parts[0].equalsIgnoreCase("ACCEPT_REQUEST")) {
                String senderEmail = normEmail(parts[1]);
                String receiverEmail = normEmail(parts[2]);
                boolean success = authService.acceptFriendRequest(senderEmail, receiverEmail);
                if (success) {
                    String receiverProfile = authService.getUserProfile(receiverEmail);
                    String receiverFirstName = receiverEmail;
                    if (receiverProfile != null && !receiverProfile.equals("ERROR")) {
                        String[] profileParts = receiverProfile.split("\\|", 2);
                        if (profileParts.length > 0 && !profileParts[0].isBlank()) {
                            receiverFirstName = profileParts[0];
                        }
                    }
                    OnlineUserManager.sendToUser(senderEmail,
                            "NOTIFICATION|FRIEND_REQUEST_ACCEPTED|" + receiverEmail + "|" + receiverFirstName);
                }
                return success ? "ACCEPT_SUCCESS" : "ACCEPT_FAILED";

            } else if (parts[0].equalsIgnoreCase("DECLINE_REQUEST")) {
                boolean success = authService.declineFriendRequest(parts[1], parts[2]);
                return success ? "DECLINE_SUCCESS" : "DECLINE_FAILED";

            } else if (parts[0].equalsIgnoreCase("GET_PENDING")) {
                String result = authService.getPendingRequests(parts[1]);
                return "PENDING|" + result;

            } else if (parts[0].equalsIgnoreCase("GET_FRIENDS") || parts[0].equalsIgnoreCase("GET_FRIEND_LIST")) {
                String result = authService.getFriendList(parts[1]);
                return "FRIENDS|" + result;

            } else if (parts[0].equalsIgnoreCase("GET_ALL_USERS")) {
                String result = authService.getAllUsers(parts[1]);
                return "ALL_USERS|" + result;

            } else if (parts[0].equalsIgnoreCase("SEARCH_USER")) {
                // SEARCH_USER|query|myEmail
                String result = authService.searchUser(parts[1], parts[2]);
                return "SEARCH_RESULT|" + result;

            } else if (parts[0].equalsIgnoreCase("GET_MY_GROUPS")) {
                String result = authService.getMyGroups(parts[1]);
                return "MY_GROUPS|" + result;

            } else if (parts[0].equalsIgnoreCase("CREATE_GROUP")) {
                // CREATE_GROUP|groupId|groupName|creatorEmail
                boolean ok = authService.createGroup(parts[1], parts[2], parts[3]);
                return ok ? "GROUP_CREATED" : "GROUP_CREATE_FAILED";

            } else if (parts[0].equalsIgnoreCase("ADD_GROUP_MEMBER")) {
                // ADD_GROUP_MEMBER|groupId|requesterEmail|newMemberEmail
                boolean ok = authService.addGroupMember(parts[1], parts[2], parts[3]);
                return ok ? "MEMBER_ADDED" : "MEMBER_ADD_FAILED";

            } else if (parts[0].equalsIgnoreCase("GET_GROUP_MEMBERS")) {
                // GET_GROUP_MEMBERS|groupId|requesterEmail
                if (parts.length < 3) return "GROUP_MEMBERS|ERROR";
                String result = authService.getGroupMembers(parts[1], parts[2]);
                return "GROUP_MEMBERS|" + result;

            } else if (parts[0].equalsIgnoreCase("SEARCH_GROUP_ADD_USERS")) {
                // SEARCH_GROUP_ADD_USERS|groupId|requesterEmail|query (query may be empty; avoid | in query)
                if (parts.length < 3) return "ERROR";
                String prefix = "SEARCH_GROUP_ADD_USERS|" + parts[1] + "|" + parts[2] + "|";
                if (!command.startsWith(prefix)) return "ERROR";
                String query = command.length() > prefix.length() ? command.substring(prefix.length()) : "";
                String result = authService.searchUsersForGroupAdd(parts[1], parts[2], query);
                if ("NOT_MEMBER".equals(result) || "ERROR".equals(result)) return result;
                return "GROUP_ADD_USERS|" + result;

            } else if (parts[0].equalsIgnoreCase("SEARCH_USERS_INVITE")) {
                // SEARCH_USERS_INVITE|requesterEmail|query — for create-group member picker
                if (parts.length < 2) return "ERROR";
                String prefix = "SEARCH_USERS_INVITE|" + parts[1] + "|";
                if (!command.startsWith(prefix)) return "ERROR";
                String query = command.length() > prefix.length() ? command.substring(prefix.length()) : "";
                String result = authService.searchUsersForInvite(parts[1], query);
                if ("ERROR".equals(result)) return result;
                return "INVITE_USERS|" + result;

            } else if (parts[0].equalsIgnoreCase("UPDATE_NAME")) {
                // UPDATE_NAME|email|firstName|lastName
                if (parts.length < 4) return "UPDATE_FAILED";
                boolean ok = authService.updateName(parts[1], parts[2], parts[3]);
                return ok ? "UPDATE_SUCCESS" : "UPDATE_FAILED";

            } else if (parts[0].equalsIgnoreCase("UPDATE_PASSWORD")) {
                // UPDATE_PASSWORD|email|newPassword|confirmPassword
                if (parts.length < 4) return "UPDATE_FAILED";
                boolean ok = authService.updatePassword(parts[1], parts[2], parts[3]);
                return ok ? "UPDATE_SUCCESS" : "UPDATE_FAILED";

            } else if (parts[0].equalsIgnoreCase("UPDATE_PICTURE")) {
                // UPDATE_PICTURE|email|base64
                String[] p = command.split("\\|", 3);
                if (p.length < 3) return "UPDATE_FAILED";
                boolean ok = authService.updateProfilePicture(p[1], p[2]);
                return ok ? "UPDATE_SUCCESS" : "UPDATE_FAILED";

            } else if (parts[0].equalsIgnoreCase("GET_GROUP_INFO")) {
                // GET_GROUP_INFO|groupId|requesterEmail
                String data = authService.getGroupInfo(parts[1], parts[2]);
                if ("NOT_MEMBER".equals(data) || "ERROR".equals(data)) return data;
                return "GROUP_INFO|" + data;

            } else if (parts[0].equalsIgnoreCase("UPDATE_GROUP_NAME")) {
                // UPDATE_GROUP_NAME|groupId|requesterEmail|newName
                if (parts.length < 4) return "UPDATE_FAILED";
                boolean ok = authService.updateGroupName(parts[1], parts[2], parts[3]);
                return ok ? "UPDATE_OK" : "UPDATE_FAILED";

            } else if (parts[0].equalsIgnoreCase("UPDATE_GROUP_PICTURE")) {
                // UPDATE_GROUP_PICTURE|groupId|requesterEmail|base64...
                String[] p = command.split("\\|", 4);
                if (p.length < 4) return "UPDATE_FAILED";
                boolean ok = authService.updateGroupPicture(p[1], p[2], p[3]);
                return ok ? "UPDATE_OK" : "UPDATE_FAILED";

            } else if (parts[0].equalsIgnoreCase("GET_MESSAGES")) {
                String result = authService.getMessages(parts[1], parts[2]);
                return result;

            } else if (parts[0].equalsIgnoreCase("GET_MESSAGES_SINCE")) {
                String result = authService.getMessagesSince(parts[1], parts[2], parts[3]);
                return result;

            } else if (parts[0].equalsIgnoreCase("GET_MESSAGES_BEFORE")) {
                // GET_MESSAGES_BEFORE|email1|email2|beforeTimestamp|limit
                int limit = parts.length > 4 ? Integer.parseInt(parts[4]) : 50;
                String result = authService.getMessagesBefore(parts[1], parts[2], parts[3], limit);
                return result;

            } else if (parts[0].equalsIgnoreCase("SEND_MESSAGE")) {
                String[] p = command.split("\\|", 4);
                if (p.length < 4) return "MESSAGE_FAILED";
                String senderEmail = normEmail(p[1]);
                String receiverEmail = normEmail(p[2]);
                String messageText = p[3];

                boolean success = authService.sendMessage(senderEmail, receiverEmail, messageText);
                if (success) {
                    String snippet = messageText.length() > 40
                            ? messageText.substring(0, 37) + "..."
                            : messageText;
                    OnlineUserManager.sendToUser(receiverEmail,
                            "NOTIFICATION|NEW_MESSAGE|" + senderEmail + "|" + snippet);
                }
                return success ? "MESSAGE_SENT" : "MESSAGE_FAILED";

            } else if (parts[0].equalsIgnoreCase("SEND_GROUP_MESSAGE")) {
                String[] p = command.split("\\|", 4);
                if (p.length < 4) return "MESSAGE_FAILED";
                boolean success = authService.sendGroupMessage(p[1], p[2], p[3]);
                return success ? "MESSAGE_SENT" : "MESSAGE_FAILED";

            } else if (parts[0].equalsIgnoreCase("GET_GROUP_MESSAGES")) {
                // GET_GROUP_MESSAGES|groupId|requesterEmail
                String result = authService.getGroupMessages(parts[1], parts[2]);
                return result;

            } else if (parts[0].equalsIgnoreCase("GET_GROUP_MESSAGES_SINCE")) {
                // GET_GROUP_MESSAGES_SINCE|groupId|sinceTimestamp|requesterEmail
                String result = authService.getGroupMessagesSince(parts[1], parts[2], parts[3]);
                return result;

            } else if (parts[0].equalsIgnoreCase("GET_GROUP_MESSAGES_BEFORE")) {
                // GET_GROUP_MESSAGES_BEFORE|groupId|beforeTimestamp|requesterEmail|limit
                int limit = parts.length > 4 ? Integer.parseInt(parts[4]) : 50;
                String result = authService.getGroupMessagesBefore(parts[1], parts[2], parts[3], limit);
                return result;

            } else if (parts[0].equalsIgnoreCase("KEEP_ALIVE")) {
                if (parts.length < 2) return "KEEP_ALIVE_FAILED";
                String email = normEmail(parts[1]);
                InetAddress addr = clientSocket.getInetAddress();
                OnlineUserManager.addUser(email, out, addr);
                persistentEmail = email;
                return "KEEP_ALIVE_SUCCESS";
            } else if (parts[0].equalsIgnoreCase("AUDIO_PORT")) {
                // AUDIO_PORT|email|port
                if (parts.length < 3) return "AUDIO_PORT_FAILED";
                String email = normEmail(parts[1]);
                int port = Integer.parseInt(parts[2]);
                OnlineUserManager.setAudioPort(email, port);
                return "AUDIO_PORT_OK";
            } else if (parts[0].equalsIgnoreCase("VIDEO_PORT")) {
                // VIDEO_PORT|email|port
                if (parts.length < 3) return "VIDEO_PORT_FAILED";
                String email = normEmail(parts[1]);
                int port = Integer.parseInt(parts[2]);
                OnlineUserManager.setVideoPort(email, port);
                return "VIDEO_PORT_OK";
            }else if (parts[0].equalsIgnoreCase("GET_SENT_REQUESTS")) {
            String result = authService.getSentRequests(parts[1]);
            return "SENT|" + result;
            }else if (parts[0].equalsIgnoreCase("CALL_INVITE")) {
                // CALL_INVITE|callerEmail|receiverEmail|callId|callerName (callerName optional)
                if (parts.length < 4) return "CALL_INVITE_FAILED";
                String from = normEmail(parts[1]);
                String to   = normEmail(parts[2]);
                String callId = parts[3];
                // parts[4] = caller's display name (optional, falls back to email)
                String callerName = parts.length >= 5 ? parts[4] : from;

                if (CallSessionManager.isInCall(from) || CallSessionManager.isInCall(to)) {
                    return "CALL_INVITE_BUSY";
                }
                CallSessionManager.create(callId, from, to);
                // Push CALL_INVITE|callId|callerEmail|callerName to receiver
                boolean ok = OnlineUserManager.sendToUser(to,
                        "CALL_INVITE|" + callId + "|" + from + "|" + callerName);
                if (!ok) {
                    CallSessionManager.remove(callId);
                    return "CALL_INVITE_OFFLINE";
                }
                return "CALL_INVITE_OK";
            } else if (parts[0].equalsIgnoreCase("CALL_ACCEPT")) {
                // CALL_ACCEPT|callId|from|to
                if (parts.length < 4) return "CALL_ACCEPT_FAILED";
                String callId = parts[1];
                String from = normEmail(parts[2]);
                String to = normEmail(parts[3]);

                CallSessionManager.CallSession s = CallSessionManager.get(callId);
                if (s == null) return "CALL_ACCEPT_UNKNOWN";

                OnlineUserManager.OnlineUserSession fromS = OnlineUserManager.getSession(from);
                OnlineUserManager.OnlineUserSession toS = OnlineUserManager.getSession(to);
                if (fromS == null || toS == null) {
                    CallSessionManager.remove(callId);
                    return "CALL_ACCEPT_OFFLINE";
                }
                if (fromS.audioPort() == null || toS.audioPort() == null) {
                    OnlineUserManager.sendToUser(to, "CALL_ACCEPT_NO_AUDIO_PORT|" + callId);
                    OnlineUserManager.sendToUser(from, "CALL_ACCEPT_NO_AUDIO_PORT|" + callId);
                    CallSessionManager.remove(callId);
                    return "CALL_ACCEPT_NO_AUDIO_PORT";
                }

                // Notify both ends with the other party endpoint (LAN/WAN IP seen by server)
                OnlineUserManager.sendToUser(from, "CALL_ESTABLISHED|" + callId + "|" + toS.address().getHostAddress() + "|" + toS.audioPort());
                OnlineUserManager.sendToUser(to, "CALL_ESTABLISHED|" + callId + "|" + fromS.address().getHostAddress() + "|" + fromS.audioPort());
                return "CALL_ACCEPT_OK";
            } else if (parts[0].equalsIgnoreCase("CALL_REJECT")) {
                // CALL_REJECT|callId|from|to|reason...
                if (parts.length < 4) return "CALL_REJECT_FAILED";
                String callId = parts[1];
                String from = normEmail(parts[2]);
                String to = normEmail(parts[3]);
                String reason = parts.length >= 5 ? command.split("\\|", 5)[4] : "REJECTED";

                OnlineUserManager.sendToUser(to, "CALL_REJECTED|" + callId + "|" + from + "|" + reason);
                CallSessionManager.remove(callId);
                return "CALL_REJECT_OK";
            } else if (parts[0].equalsIgnoreCase("CALL_HANGUP")) {
                // CALL_HANGUP|callId|from|to
                if (parts.length < 4) return "CALL_HANGUP_FAILED";
                String callId = parts[1];
                String from = normEmail(parts[2]);
                String to = normEmail(parts[3]);
                OnlineUserManager.sendToUser(to, "CALL_ENDED|" + callId + "|" + from);
                CallSessionManager.remove(callId);
                return "CALL_HANGUP_OK";
            } else if (parts[0].equalsIgnoreCase("VIDEO_INVITE")) {
                // VIDEO_INVITE|callerEmail|receiverEmail|videoId|callerName (callerName optional)
                if (parts.length < 4) return "VIDEO_INVITE_FAILED";
                String from    = normEmail(parts[1]);
                String to      = normEmail(parts[2]);
                String videoId = parts[3];
                String callerName = parts.length >= 5 ? parts[4] : from;

                if (VideoSessionManager.isInVideo(from) || VideoSessionManager.isInVideo(to)) {
                    return "VIDEO_INVITE_BUSY";
                }
                VideoSessionManager.create(videoId, from, to);
                // Push VIDEO_INVITE|videoId|callerEmail|callerName to receiver.
                // NOTE: Do NOT also push CALL_INVITE — that confuses the receiver into
                //       auto-rejecting with BUSY because callActive gets set twice.
                boolean ok = OnlineUserManager.sendToUser(to,
                        "VIDEO_INVITE|" + videoId + "|" + from + "|" + callerName);
                if (!ok) {
                    VideoSessionManager.remove(videoId);
                    return "VIDEO_INVITE_OFFLINE";
                }
                return "VIDEO_INVITE_OK";
            } else if (parts[0].equalsIgnoreCase("VIDEO_ACCEPT")) {
                // VIDEO_ACCEPT|videoId|from|to
                if (parts.length < 4) return "VIDEO_ACCEPT_FAILED";
                String videoId = parts[1];
                String from = normEmail(parts[2]);
                String to = normEmail(parts[3]);

                VideoSessionManager.VideoSession s = VideoSessionManager.get(videoId);
                if (s == null) return "VIDEO_ACCEPT_UNKNOWN";

                OnlineUserManager.OnlineUserSession fromS = OnlineUserManager.getSession(from);
                OnlineUserManager.OnlineUserSession toS = OnlineUserManager.getSession(to);
                if (fromS == null || toS == null) {
                    VideoSessionManager.remove(videoId);
                    return "VIDEO_ACCEPT_OFFLINE";
                }
                if (fromS.videoPort() == null || toS.videoPort() == null) {
                    OnlineUserManager.sendToUser(to, "VIDEO_ACCEPT_NO_VIDEO_PORT|" + videoId);
                    OnlineUserManager.sendToUser(from, "VIDEO_ACCEPT_NO_VIDEO_PORT|" + videoId);
                    VideoSessionManager.remove(videoId);
                    return "VIDEO_ACCEPT_NO_VIDEO_PORT";
                }

                // Send video endpoints
                OnlineUserManager.sendToUser(from, "VIDEO_ESTABLISHED|" + videoId + "|" + toS.address().getHostAddress() + "|" + toS.videoPort());
                OnlineUserManager.sendToUser(to, "VIDEO_ESTABLISHED|" + videoId + "|" + fromS.address().getHostAddress() + "|" + fromS.videoPort());

                // VideoCallService handles audio inherently, so we do not send CALL_ESTABLISHED.

                return "VIDEO_ACCEPT_OK";
            } else if (parts[0].equalsIgnoreCase("VIDEO_HANGUP")) {
                // VIDEO_HANGUP|videoId|from|to
                if (parts.length < 4) return "VIDEO_HANGUP_FAILED";
                String videoId = parts[1];
                String from = normEmail(parts[2]);
                String to = normEmail(parts[3]);

                OnlineUserManager.sendToUser(to, "VIDEO_ENDED|" + videoId + "|" + from);
                OnlineUserManager.sendToUser(to, "CALL_ENDED|" + videoId + "|" + from);
                VideoSessionManager.remove(videoId);
                return "VIDEO_HANGUP_OK";
            } else if (parts[0].equalsIgnoreCase("VIDEO_REJECT")) {
                // VIDEO_REJECT|videoId|from|to|reason
                if (parts.length < 4) return "VIDEO_REJECT_FAILED";
                String videoId = parts[1];
                String from = normEmail(parts[2]);
                String to = normEmail(parts[3]);
                String reason = parts.length >= 5 ? command.split("\\|", 5)[4] : "REJECTED";

                OnlineUserManager.sendToUser(to, "VIDEO_REJECTED|" + videoId + "|" + from + "|" + reason);
                VideoSessionManager.remove(videoId);
                return "VIDEO_REJECT_OK";
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "UNKNOWN_COMMAND";
    }
}