package com.codes.server;

import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages currently online users and their output streams for real-time messaging.
 * Allows pushing messages to connected users without them polling.
 */
public class OnlineUserManager {
    
    private static final ConcurrentHashMap<String, PrintWriter> onlineUsers = 
            new ConcurrentHashMap<>();
    
    /**
     * Register a user as online with their output stream
     * @param email User's email
     * @param out PrintWriter for sending messages to the client
     */
    public static void addUser(String email, PrintWriter out) {
        onlineUsers.put(email, out);
        System.out.println("User online: " + email + " (Total: " + onlineUsers.size() + ")");
    }
    
    /**
     * Remove a user from online list (user disconnected)
     * @param email User's email
     */
    public static void removeUser(String email) {
        onlineUsers.remove(email);
        System.out.println("User offline: " + email + " (Total: " + onlineUsers.size() + ")");
    }
    
    /**
     * Get the PrintWriter for a user if they're online
     * @param email User's email
     * @return PrintWriter if user is online, null otherwise
     */
    public static PrintWriter getUser(String email) {
        return onlineUsers.get(email);
    }
    
    /**
     * Check if a user is currently online
     * @param email User's email
     * @return true if online, false otherwise
     */
    public static boolean isOnline(String email) {
        return onlineUsers.containsKey(email);
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
        PrintWriter out = getUser(email);
        if (out != null) {
            out.println(message);
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
