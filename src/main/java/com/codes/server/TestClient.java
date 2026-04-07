package com.codes.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.net.Socket;

public class TestClient {
    private static String host = "localhost";

    public static void main(String[] args) throws IOException {
        System.out.print("Enter Server IP (leave empty for localhost): ");
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        String input = scanner.nextLine().trim();
        if (!input.isEmpty()) {
            host = input;
        }

        System.out.println("\n--- Starting TestClient ---");
        System.out.println("Target Server IP: " + host);
        System.out.println("---------------------------\n");
        System.out.println("1. SIGNUP:");
        System.out.println(send("SIGNUP|John|Doe|john@gmail.com|Pass123|Pass123|Pet?|Tom|"));
        System.out.println(send("SIGNUP|Jane|Doe|jane@gmail.com|Pass123|Pass123|Pet?|Cat|"));

        System.out.println("2. SEND REQUEST:");
        System.out.println(send("SEND_REQUEST|john@gmail.com|jane@gmail.com"));

        System.out.println("3. PENDING:");
        System.out.println(send("GET_PENDING|jane@gmail.com"));

        System.out.println("4. ACCEPT:");
        System.out.println(send("ACCEPT_REQUEST|john@gmail.com|jane@gmail.com"));

        System.out.println("5. FRIENDS:");
        System.out.println(send("GET_FRIENDS|john@gmail.com"));

        System.out.println("6. ALREADY FRIENDS:");
        System.out.println(send("SEND_REQUEST|john@gmail.com|jane@gmail.com"));

        System.out.println("7. SEARCH by email:");
        System.out.println(send("SEARCH_USER|john|jane@gmail.com"));

        System.out.println("8. SEARCH by name:");
        System.out.println(send("SEARCH_USER|Jane|john@gmail.com"));

        System.out.println("9. SEARCH না থাকলে:");
        System.out.println(send("SEARCH_USER|xyz123|john@gmail.com"));
    }

    private static String send(String command) {
        try (Socket socket = createSocket();
                PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            out.println(command);
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if ("END_OF_RESPONSE".equals(line))
                    break;
                if (response.length() > 0)
                    response.append("\n");
                response.append(line);
            }
            return response.toString();

        } catch (IOException e) {
            System.err.println("Failed connecting to " + host + ": " + e.getMessage());
            return "CONNECTION_ERROR";
        }
    }

    private static Socket createSocket() throws IOException {
        return new Socket(host, 4444);
    }
}
