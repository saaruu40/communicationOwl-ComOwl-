package com.codes.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class PasswordUtil {

    // Simple SHA-256 encryption (In production it is better to use BCrypt, but for beginners this is simple)
    public static String encryptPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Encryption error", e);
        }
    }

    // Method to check password
    public static boolean checkPassword(String inputPassword, String storedEncryptedPassword) {
        String encryptedInput = encryptPassword(inputPassword);
        return encryptedInput.equals(storedEncryptedPassword);
    }

    // Main method for testing
    public static void main(String[] args) {
        String password = "MySecret123";
        String encrypted = encryptPassword(password);
        System.out.println("Original: " + password);
        System.out.println("Encrypted: " + encrypted);

        boolean isMatch = checkPassword("MySecret123", encrypted);
        System.out.println("Password match: " + isMatch);  // should be true
    }
}
