package client.application;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public final class DashboardNavigator {
    private DashboardNavigator() {
    }

    public static void showLogin(Stage stage) throws IOException {
        show(stage, "/view/LogInView.fxml");
    }

    public static void showBidderDashboard(Stage stage) throws IOException {
        show(stage, "/view/BidderDashboardView.fxml");
    }

    public static void showSellerDashboard(Stage stage) throws IOException {
        show(stage, "/view/SellerDashboardView.fxml");
    }

    public static void showAdminDashboard(Stage stage) throws IOException {
        show(stage, "/view/AdminDashboardView.fxml");
    }

    private static void show(Stage stage, String viewPath) throws IOException {
        Parent root = FXMLLoader.load(DashboardNavigator.class.getResource(viewPath));
        stage.setScene(new Scene(root));
        stage.show();
    }
}
