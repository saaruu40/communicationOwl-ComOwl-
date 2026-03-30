package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Utility class for the JavaFX frontend to communicate with the backend Server.
 * Maintains both transient and persistent connections for different use cases.
 */
public class SocketClient {

    private static final String HOST = "localhost";
    private static final int PORT = 4444;
    
    // Persistent connection for keeping user online after login
    private static Socket persistentSocket = null;
    private static PrintWriter persistentOut = null;
    private static BufferedReader persistentIn = null;
    private static String currentUserEmail = null;

    /**
     * Sends a single command string to the server and returns the response line.
     * For LOGIN/SIGNUP/etc - uses transient connection.
     * Returns "CONNECTION_ERROR" if the server is unreachable.
     */
    public static String send(String command) {
        try (Socket socket = new Socket(HOST, PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {

            out.println(command);
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if ("END_OF_RESPONSE".equals(line)) break;
                if (response.length() > 0) response.append("\n");
                response.append(line);
            }
            return response.toString();

        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage());
            return "CONNECTION_ERROR";
        }
    }

    /**
     * Establishes a persistent connection to keep user ONLINE after login.
     * Must be called after successful LOGIN.
     * @param userEmail Email of logged-in user
     * @return true if persistent connection established, false otherwise
     */
    public static boolean establishPersistentConnection(String userEmail) {
        try {
            // Close any existing persistent connection first
            closePersistentConnection();
            
            persistentSocket = new Socket(HOST, PORT);
            persistentOut = new PrintWriter(persistentSocket.getOutputStream(), true);
            persistentIn = new BufferedReader(
                    new InputStreamReader(persistentSocket.getInputStream()));
            
            currentUserEmail = userEmail;
            
            // Send a KEEP_ALIVE command to register this persistent connection
            persistentOut.println("KEEP_ALIVE|" + userEmail);
            String response = persistentIn.readLine();
            
            if (response != null && response.contains("SUCCESS")) {
                System.out.println("Persistent connection established for: " + userEmail);
                return true;
            } else {
                closePersistentConnection();
                return false;
            }
            
        } catch (IOException e) {
            System.err.println("Failed to establish persistent connection: " + e.getMessage());
            closePersistentConnection();
            return false;
        }
    }

    /**
     * Closes the persistent connection (user goes offline).
     */
    public static void closePersistentConnection() {
        try {
            if (persistentOut != null) {
                persistentOut.close();
            }
            if (persistentIn != null) {
                persistentIn.close();
            }
            if (persistentSocket != null) {
                persistentSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing persistent connection: " + e.getMessage());
        } finally {
            persistentSocket = null;
            persistentOut = null;
            persistentIn = null;
            currentUserEmail = null;
        }
    }

    /**
     * Check if user has an active persistent connection
     */
    public static boolean isConnected() {
        return persistentSocket != null && !persistentSocket.isClosed();
    }

    /**
     * Get current logged-in user email
     */
    public static String getCurrentUserEmail() {
        return currentUserEmail;
    }
}
