package com.codes.server;

import com.codes.database.DatabaseManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class Server {
    private static final int PORT = 4444;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        DatabaseManager.createTables();

        while (true) {
            System.out.print("Enter IP address to run the server on (leave empty to listen on all interfaces): ");
            String ipAddress = scanner.nextLine().trim();

            try (ServerSocket serverSocket = ipAddress.isEmpty()
                    ? new ServerSocket(PORT)
                    : new ServerSocket(PORT, 0, InetAddress.getByName(ipAddress))) {

                if (ipAddress.isEmpty()) {
                    System.out.println("Server started on ALL network interfaces (port " + PORT + ").");
                } else {
                    System.out.println("Server started on " + ipAddress + ":" + PORT);
                }
                System.out.println("Waiting for clients...");

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());

                    // Separate thread for each client
                    new Thread(new ClientHandler(clientSocket)).start();
                }

            } catch (IOException e) {
                System.err.println("Server error, could not bind to "
                        + (ipAddress.isEmpty() ? "all interfaces" : ipAddress) + ": " + e.getMessage());
                System.out.println("Please try a different IP address.\n");
            }
        }
    }
}

// mvn exec:java "-Dexec.mainClass=com.codes.server.Server"