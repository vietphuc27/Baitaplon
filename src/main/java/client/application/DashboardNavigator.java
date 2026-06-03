package client.application;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;

public final class DashboardNavigator {
    private DashboardNavigator() {
    }

    public static void showLogin(Stage stage) throws IOException {
        show(stage, "/view/LogInView.fxml", true);
    }

    public static void showBidderDashboard(Stage stage) throws IOException {
        show(stage, "/view/BidderDashboardView.fxml", true);
    }

    public static void showSellerDashboard(Stage stage) throws IOException {
        show(stage, "/view/SellerDashboardView.fxml", true);
    }

    public static void showAdminDashboard(Stage stage) throws IOException {
        show(stage, "/view/AdminDashboardView.fxml", true);
    }

    private static void show(Stage stage, String viewPath, boolean fullScreen) throws IOException {
        Parent root = FXMLLoader.load(DashboardNavigator.class.getResource(viewPath));
        stage.setScene(new Scene(root));
        stage.setFullScreen(false);
        stage.show();
        if (fullScreen) {
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
            stage.setMaximized(true);
            Platform.runLater(() -> stage.setMaximized(true));
        }
    }
}
