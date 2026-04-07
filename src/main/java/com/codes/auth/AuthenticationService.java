package com.codes.auth;

import com.codes.database.DatabaseManager;
import com.codes.model.User;
import com.codes.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthenticationService {

    // Signup (create a new user)
    public boolean signUp(String firstName, String lastName,
                          String email, String password, String confirmPassword,
                          String recoveryQuestion, String recoveryAnswer, String profilePictureBase64) {

        // Check if the passwords match
        if (!password.equals(confirmPassword)) {
            System.out.println("Passwords do not match!");
            return false;
        }

        // Encrypt the password and recovery answer
        String encryptedPassword = PasswordUtil.encryptPassword(password);
        String encryptedRecoveryAnswer = PasswordUtil.encryptPassword(recoveryAnswer);

        // Normalize email case to lower-case for consistency
        String normalizedEmail = email.trim().toLowerCase();

        // If no profile picture is provided, keep it as null
        String picToSave = (profilePictureBase64 != null && !profilePictureBase64.isEmpty()) ? profilePictureBase64 : null;



        String sql = """
    INSERT INTO Users (firstName, lastName, email, password,
                       recoveryQuestion, recoveryAnswer, profilePicture)
    VALUES (?, ?, ?, ?, ?, ?, ?)
""";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, firstName);
            pstmt.setString(2, lastName);
            pstmt.setString(3, normalizedEmail);
            pstmt.setString(4, encryptedPassword);
            pstmt.setString(5, recoveryQuestion);
            pstmt.setString(6, encryptedRecoveryAnswer);
            pstmt.setString(7, picToSave);

            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                System.out.println("Account created: " + email);
                return true;
            }
            return false;

        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE") || e.getMessage().contains("PRIMARY KEY")) {
                System.out.println("An account with this email already exists!");
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }

    // Login check
    public User login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();
        String sql = "SELECT * FROM Users WHERE email = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, normalizedEmail);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedPassword = rs.getString("password");
                if (PasswordUtil.checkPassword(password, storedPassword)) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setFirstName(rs.getString("firstName"));
                    user.setLastName(rs.getString("lastName"));
                    user.setEmail(rs.getString("email"));
                    return user;
                }
            }
            return null; // wrong email or password

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Get user profile from database (firstName|lastName|profilePicBase64)
    public String getUserProfile(String email) {
        String sql = "SELECT firstName, lastName, profilePicture FROM Users WHERE email = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String firstName = rs.getString("firstName");
                String lastName = rs.getString("lastName");
                String profilePicture = rs.getString("profilePicture");
                if (firstName == null) firstName = "";
                if (lastName == null) lastName = "";
                if (profilePicture == null) profilePicture = "";
                return firstName + "|" + lastName + "|" + profilePicture;
            }
            return "ERROR";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    // Step 1: Get recovery question using email
    public String getRecoveryQuestion(String email) {
        String sql = "SELECT recoveryQuestion FROM Users WHERE email = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("recoveryQuestion");
            }
            return null; // email not found

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Check recovery answer
    public boolean checkRecoveryAnswer(String email, String answer) {
        String sql = "SELECT recoveryAnswer FROM Users WHERE email = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedAnswer = rs.getString("recoveryAnswer");
                String encryptedInput = PasswordUtil.encryptPassword(answer);
                return encryptedInput.equals(storedAnswer);
            }
            return false;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }


    // User Search by email or name
    public String searchUser(String query, String myEmail) {
        String sql = """
        SELECT email, firstName, lastName, profilePicture
        FROM Users
        WHERE (email LIKE ? OR firstName LIKE ? OR lastName LIKE ?)
        AND email != ?
        LIMIT 10
    """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchQuery = "%" + query + "%";
            pstmt.setString(1, searchQuery);
            pstmt.setString(2, searchQuery);
            pstmt.setString(3, searchQuery);
            pstmt.setString(4, myEmail);

            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(rs.getString("email"))
                        .append(":").append(rs.getString("firstName"))
                        .append(":").append(rs.getString("lastName"))
                        .append(":").append(rs.getString("profilePicture") != null ? rs.getString("profilePicture") : "");
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }


    // Step 2: Check answer and set new password + confirm password
    public boolean resetPasswordByQuestion(String email, String answer,
                                           String newPassword, String confirmNewPassword) {

        // First check: do new password and confirm password match?
        if (!newPassword.equals(confirmNewPassword)) {
            System.out.println("New password and confirm password do not match!");
            return false;
        }

        // Check the recovery answer
        String sql = "SELECT recoveryAnswer FROM Users WHERE email = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedEncryptedAnswer = rs.getString("recoveryAnswer");
                String encryptedInputAnswer = PasswordUtil.encryptPassword(answer);

                if (encryptedInputAnswer.equals(storedEncryptedAnswer)) {
                    // Answer matched → encrypt and save new password
                    String encryptedNewPass = PasswordUtil.encryptPassword(newPassword);

                    String updateSql = "UPDATE Users SET password = ? WHERE email = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, encryptedNewPass);
                        updateStmt.setString(2, email);
                        updateStmt.executeUpdate();
                    }

                    System.out.println("Password reset successful: " + email);
                    return true;
                } else {
                    System.out.println("Recovery answer did not match!");
                    return false;
                }
            }
            System.out.println("Email not found");
            return false;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Send Friend Request
    public String sendFriendRequest(String senderEmail, String receiverEmail) {
        // Cannot send a request to yourself
        if (senderEmail.equals(receiverEmail)) {
            return "CANNOT_ADD_SELF";
        }

        // Check whether the receiver exists
        String checkSql = "SELECT email FROM Users WHERE email = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(checkSql)) {

            pstmt.setString(1, receiverEmail);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) return "USER_NOT_FOUND";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }

        // Check whether a request or friendship already exists
        String checkFriendSql = """
        SELECT status FROM Friends
        WHERE (email1 = ? AND email2 = ?)
        OR (email1 = ? AND email2 = ?)
    """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(checkFriendSql)) {

            pstmt.setString(1, senderEmail);
            pstmt.setString(2, receiverEmail);
            pstmt.setString(3, receiverEmail);
            pstmt.setString(4, senderEmail);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String status = rs.getString("status");
                if (status.equals("accepted")) return "ALREADY_FRIENDS";
                if (status.equals("pending"))  return "ALREADY_REQUESTED";
                if (status.equals("blocked"))  return "BLOCKED";
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }

        // Send the request
        String sql = """
        INSERT INTO Friends (email1, email2, status)
        VALUES (?, ?, 'pending')
    """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, senderEmail);
            pstmt.setString(2, receiverEmail);
            pstmt.executeUpdate();
            return "REQUEST_SENT";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }


    public String getSentRequests(String email) {
    String sql = """
    SELECT u.email, u.firstName, u.lastName, u.profilePicture
    FROM Friends f
    JOIN Users u ON u.email = f.email2
    WHERE f.email1 = ? AND f.status = 'pending'
""";
    try (Connection conn = DatabaseManager.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {

        pstmt.setString(1, email);
        ResultSet rs = pstmt.executeQuery();

        StringBuilder sb = new StringBuilder();
        while (rs.next()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(rs.getString("email"))
                    .append(":").append(rs.getString("firstName"))
                    .append(":").append(rs.getString("lastName"));
        }
        return sb.length() > 0 ? sb.toString() : "EMPTY";

    } catch (SQLException e) {
        e.printStackTrace();
        return "ERROR";
    }
}



    // Accept Friend Request
    public boolean acceptFriendRequest(String senderEmail, String receiverEmail) {
        String sql = """
        UPDATE Friends SET status = 'accepted', acceptedAt = CURRENT_TIMESTAMP
        WHERE email1 = ? AND email2 = ? AND status = 'pending'
    """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, senderEmail);
            pstmt.setString(2, receiverEmail);
            int rows = pstmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Cancel Friend Request / Unfriend
    public boolean declineFriendRequest(String senderEmail, String receiverEmail) {
        String sql = """
        DELETE FROM Friends
        WHERE (email1 = ? AND email2 = ?)
        OR (email1 = ? AND email2 = ?)
    """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, senderEmail);
            pstmt.setString(2, receiverEmail);
            pstmt.setString(3, receiverEmail);
            pstmt.setString(4, senderEmail);
            int rows = pstmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Pending Request List (requests sent to me)
    public String getPendingRequests(String email) {
        String sql = """
        SELECT u.email, u.firstName, u.lastName, u.profilePicture
        FROM Friends f
        JOIN Users u ON u.email = f.email1
        WHERE f.email2 = ? AND f.status = 'pending'
    """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(rs.getString("email"))
                        .append(":").append(rs.getString("firstName"))
                        .append(":").append(rs.getString("lastName"));
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    // Friend List
    public String getFriendList(String email) {
        String sql = """
        SELECT u.email, u.firstName, u.lastName, u.profilePicture,
               (SELECT content FROM Messages 
                WHERE (senderEmail = ? AND receiverEmail = u.email) 
                   OR (senderEmail = u.email AND receiverEmail = ?) 
                ORDER BY timestamp DESC LIMIT 1) as lastMsg,
               (SELECT timestamp FROM Messages 
                WHERE (senderEmail = ? AND receiverEmail = u.email) 
                   OR (senderEmail = u.email AND receiverEmail = ?) 
                ORDER BY timestamp DESC LIMIT 1) as lastTime
        FROM Friends f
        JOIN Users u ON (
            CASE WHEN f.email1 = ? THEN u.email = f.email2
                 ELSE u.email = f.email1 END
        )
        WHERE (f.email1 = ? OR f.email2 = ?)
        AND f.status = 'accepted'
    """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            pstmt.setString(2, email);
            pstmt.setString(3, email);
            pstmt.setString(4, email);
            pstmt.setString(5, email);
            pstmt.setString(6, email);
            pstmt.setString(7, email);
            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) sb.append(",");
                String lastMsg = rs.getString("lastMsg");
                String lastTime = rs.getString("lastTime");
                
                // Base64 encode the message content to safely include it in the comma/colon separated list
                String encodedMsg = (lastMsg != null) ? java.util.Base64.getEncoder().encodeToString(lastMsg.getBytes(java.nio.charset.StandardCharsets.UTF_8)) : "";
                
                sb.append(rs.getString("email"))
                        .append(":").append(rs.getString("firstName"))
                        .append(":").append(rs.getString("lastName"))
                        .append(":").append(rs.getString("profilePicture") != null ? rs.getString("profilePicture") : "")
                        .append(":").append(encodedMsg)
                        .append(":").append(lastTime != null ? lastTime : "");
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    // Get All Users (excluding current user)
    public String getAllUsers(String currentUserEmail) {
        String sql = """
        SELECT email, firstName, lastName, profilePicture
        FROM Users
        WHERE email != ?
    """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, currentUserEmail);
            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(rs.getString("email"))
                        .append(":").append(rs.getString("firstName"))
                        .append(":").append(rs.getString("lastName"))
                        .append(":").append(rs.getString("profilePicture") != null ? rs.getString("profilePicture") : "");
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    // Update user name and last name
    public boolean updateName(String email, String firstName, String lastName) {
        String sql = "UPDATE Users SET firstName = ?, lastName = ? WHERE email = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, firstName);
            pstmt.setString(2, lastName);
            pstmt.setString(3, email);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Update user password
    public boolean updatePassword(String email, String newPassword, String confirmPassword) {
        if (newPassword == null || confirmPassword == null || !newPassword.equals(confirmPassword)) {
            return false;
        }
        String encrypted = PasswordUtil.encryptPassword(newPassword);
        String sql = "UPDATE Users SET password = ? WHERE email = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, encrypted);
            pstmt.setString(2, email);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Update user profile picture
    public boolean updateProfilePicture(String email, String profilePictureBase64) {
        String sql = "UPDATE Users SET profilePicture = ? WHERE email = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, profilePictureBase64);
            pstmt.setString(2, email);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean sendMessage(String senderEmail, String receiverEmail, String message) {

    String sql = """
        INSERT INTO Messages (senderEmail, receiverEmail, content)
        VALUES (?, ?, ?)
    """;

    try (Connection conn = DatabaseManager.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {

        pstmt.setString(1, senderEmail);
        pstmt.setString(2, receiverEmail);
        pstmt.setString(3, message);

        return pstmt.executeUpdate() > 0;

    } catch (SQLException e) {
        e.printStackTrace();
        return false;
    }
}
    // Get all private messages between two users (pipe-delimited, safe for message content)
    // Response format per line:  senderEmail|content|timestamp|text
    public String getMessages(String email1, String email2) {

        String sql = """
            SELECT * FROM (
                SELECT senderEmail, content, timestamp
                FROM Messages
                WHERE (senderEmail = ? AND receiverEmail = ?)
                OR (senderEmail = ? AND receiverEmail = ?)
                ORDER BY timestamp DESC
                LIMIT 50
            ) ORDER BY timestamp ASC
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email1);
            pstmt.setString(2, email2);
            pstmt.setString(3, email2);
            pstmt.setString(4, email1);

            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) sb.append("\n");
                String content = rs.getString("content");
                String type = (content != null && content.startsWith(com.codes.util.FileMessageCodec.PREFIX))
                        ? "file" : "text";
                sb.append(rs.getString("senderEmail"))
                  .append("|")
                  .append(content)
                  .append("|")
                  .append(rs.getString("timestamp"))
                  .append("|")
                  .append(type);
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    // Get only private messages AFTER a given timestamp (incremental polling) // porer shob msg niye ashe
    // Response format per line:  senderEmail|content|timestamp|text
    public String getMessagesSince(String email1, String email2, String sinceTimestamp) {

        String sql = """
            SELECT senderEmail, content, timestamp
            FROM Messages
            WHERE ((senderEmail = ? AND receiverEmail = ?)
               OR  (senderEmail = ? AND receiverEmail = ?))
            AND timestamp > ?
            ORDER BY timestamp
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email1);
            pstmt.setString(2, email2);
            pstmt.setString(3, email2);
            pstmt.setString(4, email1);
            pstmt.setString(5, sinceTimestamp);

            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) sb.append("\n");
                String content = rs.getString("content");
                String type = (content != null && content.startsWith(com.codes.util.FileMessageCodec.PREFIX))
                        ? "file" : "text";
                sb.append(rs.getString("senderEmail"))
                  .append("|")
                  .append(content)
                  .append("|")
                  .append(rs.getString("timestamp"))
                  .append("|")
                  .append(type);
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    // Get messages BEFORE a timestamp for pagination
    public String getMessagesBefore(String email1, String email2, String beforeTimestamp, int limit) {
        String sql = """
            SELECT * FROM (
                SELECT senderEmail, content, timestamp
                FROM Messages
                WHERE ((senderEmail = ? AND receiverEmail = ?)
                   OR  (senderEmail = ? AND receiverEmail = ?))
                AND timestamp < ?
                ORDER BY timestamp DESC
                LIMIT ?
            ) ORDER BY timestamp ASC
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email1);
            pstmt.setString(2, email2);
            pstmt.setString(3, email2);
            pstmt.setString(4, email1);
            pstmt.setString(5, beforeTimestamp);
            pstmt.setInt(6, limit);

            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) sb.append("\n");
                String content = rs.getString("content");
                String type = (content != null && content.startsWith(com.codes.util.FileMessageCodec.PREFIX))
                        ? "file" : "text";
                sb.append(rs.getString("senderEmail"))
                  .append("|")
                  .append(content)
                  .append("|")
                  .append(rs.getString("timestamp"))
                  .append("|")
                  .append(type);
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }
    public boolean createGroup(String groupId, String groupName, String creatorEmail) {

        String sql = "INSERT INTO Groups (groupId, groupName , creatorEmail) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupId);
            pstmt.setString(2, groupName);
            pstmt.setString(3, creatorEmail);

            pstmt.executeUpdate();

            String memberSql = "INSERT INTO GroupMembers (groupId, memberEmail) VALUES (?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(memberSql)) {

                stmt.setString(1, groupId);
                stmt.setString(2, creatorEmail);
                stmt.executeUpdate();
            }

            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** True if {@code email} is a row in GroupMembers for this group. */
    public boolean isGroupMember(String groupId, String email) {
        String sql = """
            SELECT 1 FROM GroupMembers WHERE groupId = ? AND memberEmail = ?
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupId);
            pstmt.setString(2, email);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean userExists(String email) {
        String sql = "SELECT 1 FROM Users WHERE email = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Adds {@code newMemberEmail} if {@code requesterEmail} is already a member and the new user exists.
     */
    /**
     * Group name and optional profile picture (base64) for members only.
     * Returns: {@code name|pictureBase64} with picture possibly empty, or NOT_MEMBER / ERROR.
     */
    public String getGroupInfo(String groupId, String requesterEmail) {
        if (!isGroupMember(groupId, requesterEmail)) return "NOT_MEMBER";

        String sql = """
            SELECT groupName, groupPicture FROM Groups WHERE groupId = ?
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, groupId);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) return "ERROR";
            String name = rs.getString("groupName");
            String pic = rs.getString("groupPicture");
            if (pic == null) pic = "";
            return name + "|" + pic;
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    public boolean updateGroupName(String groupId, String requesterEmail, String newName) {
        if (newName == null || newName.isBlank() || newName.contains("|")) return false;
        if (!isGroupMember(groupId, requesterEmail)) return false;

        String sql = "UPDATE Groups SET groupName = ? WHERE groupId = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newName.trim());
            pstmt.setString(2, groupId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateGroupPicture(String groupId, String requesterEmail, String pictureBase64) {
        if (!isGroupMember(groupId, requesterEmail)) return false;
        if (pictureBase64 == null) pictureBase64 = "";

        String sql = "UPDATE Groups SET groupPicture = ? WHERE groupId = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, pictureBase64.isBlank() ? null : pictureBase64);
            pstmt.setString(2, groupId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Users that can be invited when creating a group (no group id yet). Excludes only {@code requesterEmail}.
     * SQL LIKE on firstName, lastName, and email; empty query matches all.
     */
    public String searchUsersForInvite(String requesterEmail, String query) {
        if (query == null) query = "";
        String pattern = "%" + query.trim() + "%";

        String sql = """
            SELECT u.email, u.firstName, u.lastName, u.profilePicture
            FROM Users u
            WHERE u.email != ?
            AND (u.firstName LIKE ? OR u.lastName LIKE ? OR u.email LIKE ?)
            ORDER BY u.firstName, u.lastName
            LIMIT 300
            """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, requesterEmail);
            pstmt.setString(2, pattern);
            pstmt.setString(3, pattern);
            pstmt.setString(4, pattern);

            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(rs.getString("email"))
                        .append(":").append(rs.getString("firstName"))
                        .append(":").append(rs.getString("lastName"))
                        .append(":").append(rs.getString("profilePicture") != null
                        ? rs.getString("profilePicture") : "");
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    /**
     * Users eligible to be added to a group: not self, not already a member.
     * Filters with SQL LIKE on firstName, lastName, and email (empty search uses {@code %%} = all).
     */
    public String searchUsersForGroupAdd(String groupId, String requesterEmail, String query) {
        if (!isGroupMember(groupId, requesterEmail)) return "NOT_MEMBER";

        if (query == null) query = "";
        String pattern = "%" + query.trim() + "%";

        String sql = """
            SELECT u.email, u.firstName, u.lastName, u.profilePicture
            FROM Users u
            WHERE u.email != ?
            AND NOT EXISTS (
                SELECT 1 FROM GroupMembers gm
                WHERE gm.groupId = ? AND gm.memberEmail = u.email
            )
            AND (u.firstName LIKE ? OR u.lastName LIKE ? OR u.email LIKE ?)
            ORDER BY u.firstName, u.lastName
            LIMIT 300
            """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, requesterEmail);
            pstmt.setString(2, groupId);
            pstmt.setString(3, pattern);
            pstmt.setString(4, pattern);
            pstmt.setString(5, pattern);

            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(rs.getString("email"))
                        .append(":").append(rs.getString("firstName"))
                        .append(":").append(rs.getString("lastName"))
                        .append(":").append(rs.getString("profilePicture") != null
                        ? rs.getString("profilePicture") : "");
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    public boolean addGroupMember(String groupId, String requesterEmail, String newMemberEmail) {
        if (newMemberEmail == null || newMemberEmail.isBlank()) return false;
        if (!isGroupMember(groupId, requesterEmail)) return false;
        if (!userExists(newMemberEmail)) return false;
        if (isGroupMember(groupId, newMemberEmail)) return true;

        String sql = "INSERT INTO GroupMembers (groupId, memberEmail) VALUES (?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupId);
            pstmt.setString(2, newMemberEmail);

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean sendGroupMessage(String groupId, String senderEmail, String message) {
        if (!isGroupMember(groupId, senderEmail)) return false;

        String sql = """
            INSERT INTO GroupMessages (groupId, senderEmail, message)
            VALUES (?, ?, ?)
            """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupId);
            pstmt.setString(2, senderEmail);
            pstmt.setString(3, message);

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Get all group messages (pipe-delimited, safe for message content)
    // Response format per line:  senderEmail|message|timestamp
    public String getGroupMessages(String groupId, String requesterEmail) {
        if (!isGroupMember(groupId, requesterEmail)) return "NOT_MEMBER";

        String sql = """
            SELECT * FROM (
                SELECT senderEmail, message, timestamp
                FROM GroupMessages
                WHERE groupId = ?
                ORDER BY timestamp DESC
                LIMIT 50
            ) ORDER BY timestamp ASC
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupId);
            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(rs.getString("senderEmail"))
                  .append("|")
                  .append(rs.getString("message"))
                  .append("|")
                  .append(rs.getString("timestamp"));
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    // Get only group messages AFTER a given timestamp (incremental polling)
    // Response format per line:  senderEmail|message|timestamp
    public String getGroupMessagesSince(String groupId, String sinceTimestamp, String requesterEmail) {
        if (!isGroupMember(groupId, requesterEmail)) return "NOT_MEMBER";

        String sql = """
            SELECT senderEmail, message, timestamp
            FROM GroupMessages
            WHERE groupId = ?
            AND timestamp > ?
            ORDER BY timestamp
            LIMIT 50
            """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupId);
            pstmt.setString(2, sinceTimestamp);

            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(rs.getString("senderEmail"))
                  .append("|")
                  .append(rs.getString("message"))
                  .append("|")
                  .append(rs.getString("timestamp"));
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    public String getGroupMessagesBefore(String groupId, String beforeTimestamp, String requesterEmail, int limit) {
        if (!isGroupMember(groupId, requesterEmail)) return "NOT_MEMBER";

        String sql = """
            SELECT * FROM (
                SELECT senderEmail, message, timestamp
                FROM GroupMessages
                WHERE groupId = ?
                AND timestamp < ?
                ORDER BY timestamp DESC
                LIMIT ?
            ) ORDER BY timestamp ASC
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupId);
            pstmt.setString(2, beforeTimestamp);
            pstmt.setInt(3, limit);

            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(rs.getString("senderEmail"))
                  .append("|")
                  .append(rs.getString("message"))
                  .append("|")
                  .append(rs.getString("timestamp"));
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    /**
     * Members of a group (only for current members). Format per entry: email:firstName:lastName
     * Uses LEFT JOIN so every GroupMembers row appears even if Users row is missing (FK off / legacy data).
     */
    public String getGroupMembers(String groupId, String requesterEmail) {
        if (!isGroupMember(groupId, requesterEmail)) return "NOT_MEMBER";

        String sql = """
            SELECT gm.memberEmail, u.firstName, u.lastName
            FROM GroupMembers gm
            LEFT JOIN Users u ON u.email = gm.memberEmail
            WHERE gm.groupId = ?
            ORDER BY COALESCE(u.firstName, ''), COALESCE(u.lastName, ''), gm.memberEmail
            """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, groupId);
            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                String em = rs.getString("memberEmail");
                if (em == null || em.isBlank()) continue;
                if (sb.length() > 0) sb.append(",");
                String fn = rs.getString("firstName");
                String ln = rs.getString("lastName");
                if (fn == null) fn = "";
                if (ln == null) ln = "";
                sb.append(em)
                  .append(":")
                  .append(fn)
                  .append(":")
                  .append(ln);
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    // Get all groups a user is a member of
    // Response format:  groupId1:groupName1,groupId2:groupName2,...
    public String getMyGroups(String memberEmail) {

        String sql = """
            SELECT g.groupId, g.groupName
            FROM Groups g
            JOIN GroupMembers gm ON g.groupId = gm.groupId
            WHERE gm.memberEmail = ?
            ORDER BY g.createdAt DESC
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, memberEmail);
            ResultSet rs = pstmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(rs.getString("groupId"))
                  .append(":")
                  .append(rs.getString("groupName"));
            }
            return sb.length() > 0 ? sb.toString() : "EMPTY";

        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    // Test main
    public static void main(String[] args) {
        AuthenticationService auth = new AuthenticationService();

        boolean success = auth.signUp(
                "Tasnim",
                "Zannat",
                "sara@gmail.com",
                "MyPass123",
                "MyPass123",
                "What is your pet name?",
                "Tom",
                ""
        );

        System.out.println("Signup successful: " + success);

        User loggedIn = auth.login("sara@gmail.com", "MyPass123");
        if (loggedIn != null) {
            System.out.println("Login successful! Welcome " + loggedIn.getFirstName());
        } else {
            System.out.println("Login failed");
        }
    }
}