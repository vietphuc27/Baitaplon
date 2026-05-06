package com.example.btl_n3;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/view/LogInView.fxml"));
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle("Auction Application");
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        primaryStage.show();
    }

    @Override
    public void stop() {
        System.out.println("Application stopped");
    }

    public static void main(String[] args) {
        Path cacheDir = Path.of(System.getProperty("user.dir"), ".javafx-cache");
        System.setProperty("javafx.cachedir", cacheDir.toAbsolutePath().toString());
        launch(args);
    }
}
