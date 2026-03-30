package com.codes.server;

import com.codes.auth.AuthenticationService;
import com.codes.model.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final AuthenticationService authService;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.authService = new AuthenticationService();
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(
                     clientSocket.getOutputStream(), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                System.out.println("Received from client: " + line);

                String response = processCommand(line);
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
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String processCommand(String command) {
        String[] parts = command.split("\\|");

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

                User user = authService.login(parts[1], parts[2]);

                return (user != null)
                        ? "LOGIN_SUCCESS|" + user.getFirstName()
                        : "LOGIN_FAILED";

            } else if (parts[0].equalsIgnoreCase("FORGOT_QUESTION")) {

                String question = authService.getRecoveryQuestion(parts[1]);

                return (question != null)
                        ? "QUESTION|" + question
                        : "EMAIL_NOT_FOUND";

            }else if (parts[0].equalsIgnoreCase("CHECK_ANSWER")) {
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
                String result = authService.sendFriendRequest(parts[1], parts[2]);
                return result;

            } else if (parts[0].equalsIgnoreCase("ACCEPT_REQUEST")) {
                boolean success = authService.acceptFriendRequest(parts[1], parts[2]);
                return success ? "ACCEPT_SUCCESS" : "ACCEPT_FAILED";

            } else if (parts[0].equalsIgnoreCase("DECLINE_REQUEST")) {
                boolean success = authService.declineFriendRequest(parts[1], parts[2]);
                return success ? "DECLINE_SUCCESS" : "DECLINE_FAILED";

            } else if (parts[0].equalsIgnoreCase("GET_PENDING")) {
                String result = authService.getPendingRequests(parts[1]);
                return "PENDING|" + result;

            } else if (parts[0].equalsIgnoreCase("GET_FRIENDS")) {
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

            } else if (parts[0].equalsIgnoreCase("SEND_MESSAGE")) {
                boolean success = authService.sendMessage(parts[1], parts[2], parts[3]);
                return success ? "MESSAGE_SENT" : "MESSAGE_FAILED";

            } else if (parts[0].equalsIgnoreCase("SEND_GROUP_MESSAGE")) {
                boolean success = authService.sendGroupMessage(parts[1], parts[2], parts[3]);
                return success ? "MESSAGE_SENT" : "MESSAGE_FAILED";

            } else if (parts[0].equalsIgnoreCase("GET_GROUP_MESSAGES")) {
                // GET_GROUP_MESSAGES|groupId|requesterEmail
                String result = authService.getGroupMessages(parts[1], parts[2]);
                return result;

            } else if (parts[0].equalsIgnoreCase("GET_GROUP_MESSAGES_SINCE")) {
                // GET_GROUP_MESSAGES_SINCE|groupId|sinceTimestamp|requesterEmail
                String result = authService.getGroupMessagesSince(parts[1], parts[2], parts[3]);
                return result;

            } else if (parts[0].equalsIgnoreCase("KEEP_ALIVE")) {
                return "KEEP_ALIVE_SUCCESS";
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "UNKNOWN_COMMAND";
    }
}