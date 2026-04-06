// package com.example;

// import javafx.animation.FadeTransition;
// import javafx.application.Application;
// import javafx.application.Platform;
// import javafx.fxml.FXMLLoader;
// import javafx.scene.Parent;
// import javafx.scene.Scene;
// import javafx.scene.control.TextInputDialog;
// import javafx.stage.Stage;
// import java.io.IOException;
// import java.util.Optional;
// import javafx.util.Duration;
// import com.example.SocketClient;

// public class App extends Application {

//     private static Scene scene;
//     private static Stage primaryStage;

//     @Override
//     public void start(Stage stage) throws IOException {
//         primaryStage = stage;

//         // Show a dialog to get the server IP before loading the intro animation.
//         TextInputDialog dialog = new TextInputDialog("localhost");
//         dialog.setTitle("Server Connection");
//         dialog.setHeaderText("Enter Server IP Address");
//         dialog.setContentText("IP Address:");

//         Optional<String> result = dialog.showAndWait();
//         if (result.isPresent()) {
//             String ip = result.get().trim();
//             if (!ip.isEmpty()) {
//                 SocketClient.HOST = ip;
//             }
//         } else {
//             // User cancelled — exit the app
//             Platform.exit();
//             return;
//         }

//         // Load intro animation after IP is entered.
//         Parent root = FXMLLoader.load(getClass().getResource("/com/example/Intro.fxml"));
//         scene = new Scene(root, 1200, 800);

//         // Fade in intro scene
//         FadeTransition fadeIn = new FadeTransition(Duration.millis(1000), root);
//         fadeIn.setFromValue(0.0);
//         fadeIn.setToValue(1.0);
//         fadeIn.play();

//         stage.setScene(scene);
//         stage.setTitle("ComOwl");
//         stage.show();
//     }

//     static void setRoot(String fxml) throws IOException {
//         scene.setRoot(loadFXML(fxml));
//     }

//     private static Parent loadFXML(String fxml) throws IOException {
//         FXMLLoader fxmlLoader = new FXMLLoader(
//                 App.class.getResource("/com/example/" + fxml + ".fxml"));

//         // Only set root for FXML files that use fx:root (SignIn, SignUp, etc.)
//         if (fxml.equals("SignIn") || fxml.equals("SignUp") ||
//                 fxml.equals("ForgetPassword") || fxml.equals("ResetPassword")) {
//             fxmlLoader.setRoot(new javafx.scene.layout.AnchorPane());
//         }

//         return fxmlLoader.load();
//     }

//     public static Stage getStage() {
//         return primaryStage;
//     }

//     @Override
//     public void stop() {
//         // Allow graceful shutdown so webcam natively unhooks
//     }

//     public static void main(String[] args) {
//         launch();
//     }
// }
package com.example;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Optional;

public class App extends Application {

    private static Scene scene;
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;

        // ── 1. Ask for server IP ──────────────────────────────────────────────
        TextInputDialog dialog = new TextInputDialog("localhost");
        dialog.setTitle("Server Connection");
        dialog.setHeaderText("Enter Server IP Address");
        dialog.setContentText("IP Address:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String ip = result.get().trim();
            if (!ip.isEmpty()) {
                SocketClient.HOST = ip;
            }
        } else {
            Platform.exit();
            return;
        }

        // ── 2. Load ComOwlParticles.fxml (replaces Intro.fxml) ───────────────
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/example/ComOwlParticles.fxml"));
        Parent root = loader.load();

        // ── 3. Wire mouse tracking in the particle controller ─────────────────
        ComOwlParticleController ctrl = loader.getController();

        scene = new Scene(root, 900, 620);

        // Must bind AFTER scene is created
        ctrl.bindScene(scene);

        // ── 4. Fade in ────────────────────────────────────────────────────────
        FadeTransition fadeIn = new FadeTransition(Duration.millis(1000), root);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.play();

        stage.setScene(scene);
        stage.setTitle("ComOwl");
        stage.setResizable(true);
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setMaximized(true);
        stage.show();
    }

    // ── Scene switcher (used by SignIn, SignUp, etc.) ─────────────────────────
    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
        if (primaryStage != null) {
            primaryStage.setMaximized(true);
        }
    }

    static FXMLLoader setRootWithLoader(String fxml) throws IOException {
        FXMLLoader loader = getLoader(fxml);
        scene.setRoot(loader.load());
        if (primaryStage != null) {
            primaryStage.setMaximized(true);
        }
        return loader;
    }

    private static Parent loadFXML(String fxml) throws IOException {
        return getLoader(fxml).load();
    }

    private static FXMLLoader getLoader(String fxml) {
        FXMLLoader fxmlLoader = new FXMLLoader(
                App.class.getResource("/com/example/" + fxml + ".fxml"));

        if (fxml.equals("SignIn") || fxml.equals("SignUp") ||
                fxml.equals("ForgetPassword") || fxml.equals("ResetPassword")) {
            fxmlLoader.setRoot(new javafx.scene.layout.AnchorPane());
        }

        return fxmlLoader;
    }

    public static Stage getStage() {
        return primaryStage;
    }

    @Override
    public void stop() {
        // Graceful shutdown — webcam etc. unhooks naturally
    }

    public static void main(String[] args) {
        launch();
    }
}