package com.example;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamException;

public class TestWebcam {
    public static void main(String[] args) {
        System.out.println("Starting Webcam Test...");
        try {
            Webcam webcam = Webcam.getDefault();
            if (webcam != null) {
                System.out.println("Webcam found: " + webcam.getName());
                try {
                    webcam.open();
                    System.out.println("Webcam opened successfully! Checking if it's open: " + webcam.isOpen());
                    webcam.close();
                    System.out.println("Webcam closed successfully.");
                } catch (WebcamException e) {
                    System.err.println("WebcamException during open: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("Webcam.getDefault() returned null. No webcam detected!");
            }
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
