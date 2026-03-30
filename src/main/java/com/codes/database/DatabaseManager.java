package com.codes.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:pingMe.db";

    static {
        // Load SQLite JDBC driver
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found!");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void createTables() {

        // Users table
        String createUsersTable = """
        CREATE TABLE IF NOT EXISTS Users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            firstName TEXT NOT NULL,
            lastName TEXT NOT NULL,
            email TEXT UNIQUE NOT NULL,
            password TEXT NOT NULL,
            recoveryQuestion TEXT NOT NULL,
            recoveryAnswer TEXT NOT NULL,
            profilePicture TEXT,
            createdAt DATETIME DEFAULT CURRENT_TIMESTAMP
        );
        """;

        // Friends table
        String createFriendsTable = """
        CREATE TABLE IF NOT EXISTS Friends (
            email1 TEXT NOT NULL,
            email2 TEXT NOT NULL,
            status TEXT NOT NULL,
            requestedAt DATETIME DEFAULT CURRENT_TIMESTAMP,
            acceptedAt DATETIME,
            PRIMARY KEY (email1, email2),
            FOREIGN KEY (email1) REFERENCES Users(email),
            FOREIGN KEY (email2) REFERENCES Users(email)
        );
        """;

        // Private messages table
        String createMessagesTable = """
        CREATE TABLE IF NOT EXISTS Messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            senderEmail TEXT NOT NULL,
            receiverEmail TEXT NOT NULL,
            content TEXT NOT NULL,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
            isRead BOOLEAN DEFAULT 0,
            FOREIGN KEY (senderEmail) REFERENCES Users(email),
            FOREIGN KEY (receiverEmail) REFERENCES Users(email)
        );
        """;

        // Groups table
        String createGroupsTable = """
        CREATE TABLE IF NOT EXISTS Groups (
            groupId TEXT PRIMARY KEY,
            groupName TEXT NOT NULL,
            creatorEmail TEXT NOT NULL,
            createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (creatorEmail) REFERENCES Users(email)
        );
        """;

        // Group members table
        String createGroupMembersTable = """
        CREATE TABLE IF NOT EXISTS GroupMembers (
            groupId TEXT NOT NULL,
            memberEmail TEXT NOT NULL,
            joinedAt DATETIME DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (groupId, memberEmail),
            FOREIGN KEY (groupId) REFERENCES Groups(groupId),
            FOREIGN KEY (memberEmail) REFERENCES Users(email)
        );
        """;

        // Group messages table
        String createGroupMessagesTable = """
        CREATE TABLE IF NOT EXISTS GroupMessages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            groupId TEXT NOT NULL,
            senderEmail TEXT NOT NULL,
            message TEXT NOT NULL,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (groupId) REFERENCES Groups(groupId),
            FOREIGN KEY (senderEmail) REFERENCES Users(email)
        );
        """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Create tables
            stmt.execute(createUsersTable);
            stmt.execute(createFriendsTable);
            stmt.execute(createMessagesTable);
            stmt.execute(createGroupsTable);
            stmt.execute(createGroupMembersTable);
            stmt.execute(createGroupMessagesTable);

            try {
                stmt.execute("ALTER TABLE Groups ADD COLUMN groupPicture TEXT");
                System.out.println("- Groups.groupPicture column ensured");
            } catch (SQLException ignored) {
                // Column already exists on older DBs
            }

            System.out.println("Database initialized successfully.");
            System.out.println("Tables created:");
            System.out.println("- Users");
            System.out.println("- Friends");
            System.out.println("- Messages (Private Chat)");
            System.out.println("- Groups");
            System.out.println("- GroupMembers");
            System.out.println("- GroupMessages");

        } catch (SQLException e) {
            System.err.println("Error creating tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Run this to initialize database
    public static void main(String[] args) {
        createTables();
    }
}