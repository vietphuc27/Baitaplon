package com.example.btl_n3;

import client.application.DashboardNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Auction Application");
        showLoginFullscreen(primaryStage);
    }

    private void showLoginFullscreen(Stage stage) throws IOException {
        DashboardNavigator.showLogin(stage);
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
