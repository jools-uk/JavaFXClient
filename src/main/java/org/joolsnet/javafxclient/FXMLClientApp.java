package org.joolsnet.javafxclient;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class FXMLClientApp extends Application {

    private FXMLClient client;
    private UIController uiController;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("JavaFX FXML Client");

        client = new FXMLClient("localhost", 8080);
        uiController = new UIController(client);

        BorderPane root = uiController.createUI();

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (uiController != null) {
            uiController.cleanup();
        }
        if (client != null) {
            client.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
