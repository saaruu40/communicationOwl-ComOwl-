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
                out.println(response);
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
            } else if (parts[0].equalsIgnoreCase("SEARCH_USER")) {
            // SEARCH_USER|query|myEmail
            String result = authService.searchUser(parts[1], parts[2]);
            return "SEARCH_RESULT|" + result;
        }


        } catch (Exception e) {
            e.printStackTrace();
        }

        return "UNKNOWN_COMMAND";
    }
}