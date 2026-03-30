package com.example;

import java.time.LocalDateTime;

/**
 * Represents a single chat message in the PingOwl application.
 */
public class ChatMessage {
    private String senderEmail;
    private String recipientEmail;
    private String messageContent;
    private LocalDateTime timestamp;
    private String messageType; // "text", "image", "voice", etc.

    public ChatMessage(String senderEmail, String recipientEmail, String messageContent, 
                       LocalDateTime timestamp, String messageType) {
        this.senderEmail = senderEmail;
        this.recipientEmail = recipientEmail;
        this.messageContent = messageContent;
        this.timestamp = timestamp;
        this.messageType = messageType;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "senderEmail='" + senderEmail + '\'' +
                ", recipientEmail='" + recipientEmail + '\'' +
                ", messageContent='" + messageContent + '\'' +
                ", timestamp=" + timestamp +
                ", messageType='" + messageType + '\'' +
                '}';
    }
}
