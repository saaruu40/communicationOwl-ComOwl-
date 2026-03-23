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
            pstmt.setString(3, email);
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
        String sql = "SELECT * FROM Users WHERE email = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
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
                        .append(":").append(rs.getString("lastName"));
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
        SELECT u.email, u.firstName, u.lastName, u.profilePicture
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
