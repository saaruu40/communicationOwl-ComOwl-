package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Utility class for the JavaFX frontend to communicate with the backend Server.
 * Each call opens a short-lived connection, sends one command, reads one response, then closes.
 */
public class SocketClient {

    private static final String HOST = "localhost";
    private static final int    PORT = 4444;

    /**
     * Sends a single command string to the server and returns the response line.
     * Returns "CONNECTION_ERROR" if the server is unreachable.
     */
    public static String send(String command) {
        try (Socket socket = new Socket(HOST, PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {

            out.println(command);
            return in.readLine();

        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage());
            return "CONNECTION_ERROR";
        }
    }
}
