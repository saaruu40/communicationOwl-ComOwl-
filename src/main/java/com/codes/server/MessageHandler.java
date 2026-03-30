// Server-Side Message Handling Module
// This file outlines the server-side implementation needed for ChatRoomController

package com.codes.server;

import java.util.*;

/**
 * Handle message-related operations on the server side.
 * This is a reference implementation for the protocol commands
 * that ChatRoomController expects.
 */
public class MessageHandler {

    // Pseudo-code for server implementation
    
    /**
     * Get list of friends for a user
     * Protocol: GET_FRIENDS|userEmail
     * Response: friend1@email.com|friend2@email.com|friend3@email.com
     */
    public static String getFriendsList(String userEmail) {
        /*
        SELECT DISTINCT 
            CASE 
                WHEN user1 = userEmail THEN user2 
                ELSE user1 
            END as friend_email
        FROM friends 
        WHERE (user1 = userEmail OR user2 = userEmail) AND status = 'ACCEPTED'
        */
        
        // Return format: email1@domain.com|email2@domain.com|email3@domain.com
        return null;
    }

    /**
     * Get message history between two users
     * Protocol: GET_MESSAGES|userEmail1|userEmail2
     * Response: (multiple lines)
     * sender@email.com|message_text|timestamp|message_type
     */
    public static String getMessageHistory(String userEmail1, String userEmail2) {
        /*
        SELECT sender_email, message_content, timestamp, message_type
        FROM messages
        WHERE (sender_email = userEmail1 AND recipient_email = userEmail2)
           OR (sender_email = userEmail2 AND recipient_email = userEmail1)
        ORDER BY timestamp ASC
        LIMIT 100  -- Last 100 messages
        */
        
        // Build response with newline-separated messages
        StringBuilder response = new StringBuilder();
        /*
        for each message:
            response.append(message.sender_email).append("|")
                   .append(message.content).append("|")
                   .append(message.timestamp).append("|")
                   .append(message.type).append("\n");
        */
        
        return response.toString();
    }

    /**
     * Send a message from one user to another
     * Protocol: SEND_MESSAGE|senderEmail|recipientEmail|messageText|timestamp
     * Response: MESSAGE_SENT|messageId
     */
    public static String sendMessage(String senderEmail, String recipientEmail, 
                                     String messageText, String timestamp) {
        /*
        INSERT INTO messages (
            sender_email, recipient_email, message_content, 
            timestamp, message_type, is_read
        ) VALUES (
            senderEmail, recipientEmail, messageText, 
            timestamp, 'text', false
        )
        */
        
        // Return success response with message ID
        return "MESSAGE_SENT|" + generateMessageId();
    }

    /**
     * Mark messages as read
     * Protocol: MARK_READ|userEmail|otherUserEmail
     * Response: MARKED_READ
     */
    public static String markMessagesAsRead(String userEmail, String otherUserEmail) {
        /*
        UPDATE messages 
        SET is_read = true
        WHERE recipient_email = userEmail 
          AND sender_email = otherUserEmail 
          AND is_read = false
        */
        
        return "MARKED_READ";
    }

    /**
     * Get unread message count
     * Protocol: GET_UNREAD_COUNT|userEmail
     * Response: friend1@email.com:5|friend2@email.com:2
     */
    public static String getUnreadCounts(String userEmail) {
        /*
        SELECT sender_email, COUNT(*) as unread_count
        FROM messages
        WHERE recipient_email = userEmail AND is_read = false
        GROUP BY sender_email
        */
        
        // Return format: sender1@email.com:5|sender2@email.com:2
        return null;
    }

    /**
     * Delete a message
     * Protocol: DELETE_MESSAGE|messageId
     * Response: MESSAGE_DELETED|messageId
     */
    public static String deleteMessage(String messageId) {
        /*
        DELETE FROM messages WHERE message_id = messageId
        */
        
        return "MESSAGE_DELETED|" + messageId;
    }

    /**
     * Search messages
     * Protocol: SEARCH_MESSAGES|userEmail|searchText
     * Response: (multiple lines of messages containing searchText)
     */
    public static String searchMessages(String userEmail, String searchText) {
        /*
        SELECT sender_email, message_content, timestamp, message_type
        FROM messages
        WHERE (sender_email = userEmail OR recipient_email = userEmail)
          AND message_content LIKE '%searchText%'
        ORDER BY timestamp DESC
        */
        
        return null;
    }

    /**
     * Get conversation list with latest message
     * Protocol: GET_CONVERSATIONS|userEmail
     * Response: (multiple lines)
     * otherUser@email.com|last_message|timestamp|unread_count
     */
    public static String getConversations(String userEmail) {
        /*
        SELECT 
            CASE WHEN sender_email = userEmail THEN recipient_email ELSE sender_email END as other_user,
            (SELECT message_content FROM messages m2 
             WHERE (m2.sender_email = m.sender_email AND m2.recipient_email = m.recipient_email)
                OR (m2.sender_email = m.recipient_email AND m2.recipient_email = m.sender_email)
             ORDER BY timestamp DESC LIMIT 1) as last_message,
            (SELECT timestamp FROM messages m2 
             WHERE (m2.sender_email = m.sender_email AND m2.recipient_email = m.recipient_email)
                OR (m2.sender_email = m.recipient_email AND m2.recipient_email = m.sender_email)
             ORDER BY timestamp DESC LIMIT 1) as last_timestamp,
            (SELECT COUNT(*) FROM messages m3 
             WHERE recipient_email = userEmail AND sender_email = other_user AND is_read = false) as unread_count
        FROM messages m
        WHERE sender_email = userEmail OR recipient_email = userEmail
        GROUP BY other_user
        ORDER BY last_timestamp DESC
        */
        
        return null;
    }

    // ============ Helper Methods ============
    
    private static String generateMessageId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Database Schema Reference
     * 
     * CREATE TABLE messages (
     *     message_id VARCHAR(36) PRIMARY KEY,
     *     sender_email VARCHAR(255) NOT NULL,
     *     recipient_email VARCHAR(255) NOT NULL,
     *     message_content TEXT,
     *     timestamp DATETIME NOT NULL,
     *     message_type VARCHAR(50) DEFAULT 'text',  -- 'text', 'image', 'voice', etc.
     *     is_read BOOLEAN DEFAULT false,
     *     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     *     FOREIGN KEY (sender_email) REFERENCES users(email),
     *     FOREIGN KEY (recipient_email) REFERENCES users(email),
     *     INDEX idx_participants (sender_email, recipient_email),
     *     INDEX idx_timestamp (timestamp)
     * );
     * 
     * CREATE TABLE friends (
     *     friend_id INT AUTO_INCREMENT PRIMARY KEY,
     *     user1 VARCHAR(255) NOT NULL,
     *     user2 VARCHAR(255) NOT NULL,
     *     status ENUM('PENDING', 'ACCEPTED', 'BLOCKED') DEFAULT 'PENDING',
     *     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     *     FOREIGN KEY (user1) REFERENCES users(email),
     *     FOREIGN KEY (user2) REFERENCES users(email),
     *     UNIQUE KEY unique_friendship (user1, user2),
     *     INDEX idx_status (status)
     * );
     */
}
