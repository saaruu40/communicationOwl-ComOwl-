package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.Optional;
import com.example.SocketClient;

public class App extends Application {

    private static Scene scene;
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;

        // Show a dialog to get the server IP before loading the main UI
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
            // User cancelled — exit the app
            Platform.exit();
            return;
        }

        scene = new Scene(loadFXML("SignUp"), 1200, 800);
        stage.setScene(scene);
        stage.show();
    }

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
                App.class.getResource("/com/example/" + fxml + ".fxml"));

        // Only set root for FXML files that use fx:root (SignIn, SignUp, etc.)
        if (fxml.equals("SignIn") || fxml.equals("SignUp") ||
                fxml.equals("ForgetPassword") || fxml.equals("ResetPassword")) {
            fxmlLoader.setRoot(new javafx.scene.layout.AnchorPane());
        }

        return fxmlLoader.load();
    }

    public static Stage getStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch();
    }
}