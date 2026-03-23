package com.codes.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:pingMe.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void createTables() {
        // Users table (no userId, email itself is the unique ID)
        String createUsersTable = """
        CREATE TABLE IF NOT EXISTS Users (
            id                  INTEGER PRIMARY KEY AUTOINCREMENT,
            firstName           TEXT NOT NULL,
            lastName            TEXT NOT NULL,
            email               TEXT UNIQUE NOT NULL,
            password            TEXT NOT NULL,
            recoveryQuestion    TEXT NOT NULL,
            recoveryAnswer      TEXT NOT NULL,
            profilePicture      TEXT,
            createdAt           DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    """;

        // Friends table (using email1/email2 instead of userId1/userId2)
        String createFriendsTable = """
        CREATE TABLE IF NOT EXISTS Friends (
            email1      TEXT NOT NULL,
            email2      TEXT NOT NULL,
            status      TEXT NOT NULL,   -- 'accepted', 'pending', 'blocked'
            requestedAt DATETIME DEFAULT CURRENT_TIMESTAMP,
            acceptedAt  DATETIME,
            PRIMARY KEY (email1, email2),
            FOREIGN KEY (email1) REFERENCES Users(email),
            FOREIGN KEY (email2) REFERENCES Users(email)
        )
    """;

        // Messages table (using senderEmail/receiverEmail instead of senderId/receiverId)
        String createMessagesTable = """
        CREATE TABLE IF NOT EXISTS Messages (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            senderEmail     TEXT NOT NULL,
            receiverEmail   TEXT NOT NULL,
            content         TEXT NOT NULL,
            timestamp       DATETIME DEFAULT CURRENT_TIMESTAMP,
            isRead          BOOLEAN DEFAULT 0,
            FOREIGN KEY (senderEmail)   REFERENCES Users(email),
            FOREIGN KEY (receiverEmail) REFERENCES Users(email)
        )
    """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createUsersTable);
            stmt.execute(createFriendsTable);
            stmt.execute(createMessagesTable);

            System.out.println("All tables created successfully (or already existed).");
            System.out.println("- Users (email as primary key)");
            System.out.println("- Friends");
            System.out.println("- Messages");

        } catch (SQLException e) {
            System.err.println("Error creating tables: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // Test main method
    public static void main(String[] args) {
        createTables();
    }
}

//mvn exec:java "-Dexec.mainClass=com.codes.database.DatabaseManager"