package com.codes.server;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages currently online users and their output streams for real-time messaging.
 * Allows pushing messages to connected users without them polling.
 */
public class OnlineUserManager {
    private static String normEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }
    
    public static final class OnlineUserSession {
        private final PrintWriter out;
        private final InetAddress address;
        private volatile Integer audioPort;

        OnlineUserSession(PrintWriter out, InetAddress address) {
            this.out = out;
            this.address = address;
        }

        public PrintWriter out() { return out; }
        public InetAddress address() { return address; }
        public Integer audioPort() { return audioPort; }
        public void setAudioPort(Integer port) { this.audioPort = port; }
    }

    private static final ConcurrentHashMap<String, OnlineUserSession> onlineUsers =
            new ConcurrentHashMap<>();
    
    /**
     * Register a user as online with their output stream
     * @param email User's email
     * @param out PrintWriter for sending messages to the client
     */
    public static void addUser(String email, PrintWriter out, InetAddress address) {
        String key = normEmail(email);
        if (key == null || key.isBlank()) return;
        onlineUsers.put(key, new OnlineUserSession(out, address));
        System.out.println("User online: " + email + " (Total: " + onlineUsers.size() + ")");
    }

    public static void setAudioPort(String email, int port) {
        OnlineUserSession s = onlineUsers.get(normEmail(email));
        if (s != null) s.setAudioPort(port);
    }
    
    /**
     * Remove a user from online list (user disconnected)
     * @param email User's email
     */
    public static void removeUser(String email) {
        onlineUsers.remove(normEmail(email));
        System.out.println("User offline: " + email + " (Total: " + onlineUsers.size() + ")");
    }
    
    /**
     * Get the PrintWriter for a user if they're online
     * @param email User's email
     * @return PrintWriter if user is online, null otherwise
     */
    public static PrintWriter getUser(String email) {
        OnlineUserSession s = onlineUsers.get(normEmail(email));
        return s == null ? null : s.out();
    }

    public static OnlineUserSession getSession(String email) {
        return onlineUsers.get(normEmail(email));
    }
    
    /**
     * Check if a user is currently online
     * @param email User's email
     * @return true if online, false otherwise
     */
    public static boolean isOnline(String email) {
        return onlineUsers.containsKey(normEmail(email));
    }
    
    /**
     * Get number of currently online users
     * @return Count of online users
     */
    public static int getOnlineCount() {
        return onlineUsers.size();
    }
    
    /**
     * Broadcast a message to specific users
     * @param emails Array of user emails
     * @param message Message to send
     */
    public static void broadcastToUsers(String[] emails, String message) {
        for (String email : emails) {
            PrintWriter out = getUser(email);
            if (out != null) {
                out.println(message);
            }
        }
    }
    
    /**
     * Send message to a specific user if online
     * @param email User's email
     * @param message Message to send
     * @return true if message was sent, false if user is offline
     */
    public static boolean sendToUser(String email, String message) {
        OnlineUserSession s = getSession(email);
        if (s != null && s.out() != null) {
            s.out().println(message);
            return true;
        }
        return false;
    }
    
    /**
     * Get list of all currently online users
     * @return Space-separated list of online emails
     */
    public static String getOnlineUsers() {
        return String.join(", ", onlineUsers.keySet());
    }
    
    /**
     * Clear all users (used for shutdown)
     */
    public static void clearAll() {
        onlineUsers.clear();
        System.out.println("All users disconnected");
    }
}
