package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Utility class for the JavaFX frontend to communicate with the backend Server.
 * Maintains both transient and persistent connections for different use cases.
 */
public class SocketClient {

    public static String HOST = "localhost";
    private static final int PORT = 4444;
    public static String CLIENT_IP = null;

    public static void promptForIPs() {
        if (System.console() == null && System.in == null) {
            System.err.println("No console attached, using default IPs.");
            return; // GUI run without console
        }

        try {
            java.util.Scanner scanner = new java.util.Scanner(System.in);
            System.out.print("Enter Server IP address to connect (leave empty for localhost): ");
            if (scanner.hasNextLine()) {
                String serverIpAddress = scanner.nextLine().trim();
                if (!serverIpAddress.isEmpty()) {
                    HOST = serverIpAddress;
                }
            }

            System.out.print("Enter Client IP address to bind (leave empty for default): ");
            if (scanner.hasNextLine()) {
                String clientIpAddress = scanner.nextLine().trim();
                if (!clientIpAddress.isEmpty()) {
                    CLIENT_IP = clientIpAddress;
                }
            }
        } catch (Exception e) {
            System.err.println("Could not read IP address from console. Using default instead.");
        }
    }

    public static String getClientIp() {
        return CLIENT_IP;
    }

    private static Socket createSocket() throws IOException {
        if (CLIENT_IP != null && !CLIENT_IP.trim().isEmpty() && !"localhost".equalsIgnoreCase(CLIENT_IP)) {
            return new Socket(HOST, PORT, java.net.InetAddress.getByName(CLIENT_IP), 0);
        }
        return new Socket(HOST, PORT);
    }

    // Persistent connection for keeping user online after login
    private static Socket persistentSocket = null;
    private static PrintWriter persistentOut = null;
    private static BufferedReader persistentIn = null;
    private static String currentUserEmail = null;
    private static Thread persistentListenerThread = null;
    private static final List<Consumer<String>> persistentListeners = new CopyOnWriteArrayList<>();

    /**
     * Sends a single command string to the server and returns the response line.
     * For LOGIN/SIGNUP/etc - uses transient connection.
     * Returns "CONNECTION_ERROR" if the server is unreachable.
     */
    public static String send(String command) {
        try (Socket socket = createSocket();
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()))) {

            out.println(command);
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if ("END_OF_RESPONSE".equals(line))
                    break;
                if (response.length() > 0)
                    response.append("\n");
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
     * 
     * @param userEmail Email of logged-in user
     * @return true if persistent connection established, false otherwise
     */
    public static boolean establishPersistentConnection(String userEmail) {
        try {
            // Close any existing persistent connection first
            closePersistentConnection();

            persistentSocket = createSocket();
            persistentOut = new PrintWriter(persistentSocket.getOutputStream(), true);
            persistentIn = new BufferedReader(
                    new InputStreamReader(persistentSocket.getInputStream()));

            currentUserEmail = userEmail;

            // Send a KEEP_ALIVE command to register this persistent connection
            persistentOut.println("KEEP_ALIVE|" + userEmail);
            String response = persistentIn.readLine();

            if (response != null && response.contains("SUCCESS")) {
                System.out.println("Persistent connection established for: " + userEmail);
                startPersistentListener();
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

    private static void startPersistentListener() {
        if (persistentListenerThread != null && persistentListenerThread.isAlive())
            return;
        persistentListenerThread = new Thread(() -> {
            try {
                String line;
                while (persistentIn != null && (line = persistentIn.readLine()) != null) {
                    System.out.println("[persistent] got: " + line);
                    if ("END_OF_RESPONSE".equals(line))
                        continue;
                    for (Consumer<String> c : persistentListeners) {
                        try {
                            c.accept(line);
                        } catch (Exception e) {
                            System.err.println("Error in persistent listener: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Persistent listener terminated: " + e.getMessage());
            }
        }, "persistent-socket-listener");
        persistentListenerThread.setDaemon(true);
        persistentListenerThread.start();
    }

    public static void addPersistentListener(Consumer<String> listener) {
        if (listener != null)
            persistentListeners.add(listener);
    }

    public static void removePersistentListener(Consumer<String> listener) {
        if (listener != null)
            persistentListeners.remove(listener);
    }

    public static boolean sendPersistent(String command) {
        if (persistentOut == null)
            return false;
        persistentOut.println(command);
        return true;
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
            persistentListenerThread = null;
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
