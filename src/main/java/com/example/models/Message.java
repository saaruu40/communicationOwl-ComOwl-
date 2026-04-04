package com.example.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message {
    private String id;
    private String sender;
    private String recipient;
    private String text;
    private LocalDateTime timestamp;
    private boolean isSent;
    private boolean isRead;
    private String messageType; // "text", "image", "file", "voice"
    
    public Message() {}
    
    public Message(String sender, String text, LocalDateTime timestamp, boolean isSent) {
        this.sender = sender;
        this.text = text;
        this.timestamp = timestamp;
        this.isSent = isSent;
        this.messageType = "text";
    }
    
    public Message(String sender, String recipient, String text, LocalDateTime timestamp) {
        this.sender = sender;
        this.recipient = recipient;
        this.text = text;
        this.timestamp = timestamp;
        this.messageType = "text";
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public boolean isSent() { return isSent; }
    public void setSent(boolean sent) { isSent = sent; }
    
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    
    public String getFormattedTime() {
        if (timestamp == null) return "";
        return timestamp.format(DateTimeFormatter.ofPattern("h:mm a"));
    }
}