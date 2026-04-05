package com.example;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-user Gemini AI chat sessions.
 * Each user (identified by email) gets an isolated conversation context.
 * Reads the API key from the GEMINI_API_KEY environment variable.
 */
public class GeminiChatService {

    private final Client client;
    private final Map<String, java.util.List<Map<String, String>>> userHistories = new ConcurrentHashMap<>();
    private final String modelName = "gemini-2.0-flash";

    /**
     * Creates a new GeminiChatService.
     * @throws IllegalStateException if GEMINI_API_KEY environment variable is not set.
     */
    public GeminiChatService() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY environment variable is not set. " +
                    "Please set it before launching the application.");
        }
        this.client = Client.builder().apiKey(apiKey).build();
    }

    /**
     * Sends a question to Gemini for the given user and returns the AI response.
     * Conversation history is maintained per user for contextual follow-up replies.
     *
     * @param userEmail the user's email (used as session key)
     * @param question  the user's question
     * @return the AI response text, or an error message on failure
     */
    public String ask(String userEmail, String question) {
        if (userEmail == null || userEmail.isBlank()) {
            return "Error: No user session.";
        }
        if (question == null || question.isBlank()) {
            return "Error: Empty question.";
        }

        try {
            // Get or create conversation history for this user
            java.util.List<Map<String, String>> history = userHistories.computeIfAbsent(
                    userEmail.toLowerCase(), k -> new java.util.ArrayList<>());

            // Build the contents list with conversation history
            java.util.List<com.google.genai.types.Content> contents = new java.util.ArrayList<>();

            // Add conversation history as alternating user/model turns
            for (Map<String, String> turn : history) {
                contents.add(com.google.genai.types.Content.builder()
                        .role(turn.get("role"))
                        .parts(java.util.List.of(
                                com.google.genai.types.Part.builder()
                                        .text(turn.get("text"))
                                        .build()))
                        .build());
            }

            // Add the new user message
            contents.add(com.google.genai.types.Content.builder()
                    .role("user")
                    .parts(java.util.List.of(
                            com.google.genai.types.Part.builder()
                                    .text(question)
                                    .build()))
                    .build());

            // Call the API
            GenerateContentResponse response = client.models.generateContent(modelName, contents, null);

            String answer = response.text();
            if (answer == null || answer.isBlank()) {
                answer = "(No response from AI)";
            }

            // Store the turns in history for future context
            history.add(Map.of("role", "user", "text", question));
            history.add(Map.of("role", "model", "text", answer));

            // Limit history to last 20 turns (10 exchanges) to avoid token overflow
            while (history.size() > 40) {
                history.remove(0);
                history.remove(0);
            }

            return answer;

        } catch (Exception e) {
            System.err.println("[GeminiChatService] Error for user " + userEmail + ": " + e.getMessage());
            e.printStackTrace();
            return "Sorry, I could not reach the AI service. (" + e.getMessage() + ")";
        }
    }

    /**
     * Clears the conversation history for a specific user.
     */
    public void clearHistory(String userEmail) {
        if (userEmail != null) {
            userHistories.remove(userEmail.toLowerCase());
        }
    }

    /**
     * Checks if the service is properly initialized with an API key.
     */
    public boolean isReady() {
        return client != null;
    }
}
