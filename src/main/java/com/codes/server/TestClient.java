package com.codes.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class TestClient {
    public static void main(String[] args) throws IOException {

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
        try (Socket socket = new Socket("localhost", 4444);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {
            out.println(command);
            return in.readLine();
        } catch (IOException e) {
            return "CONNECTION_ERROR";
        }
    }
}
