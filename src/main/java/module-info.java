module view.btl {
    requires javafx.controls;
    requires javafx.fxml;


    opens common.models.user to javafx.fxml;
    opens common.models.entity to javafx.fxml;
    opens common.models.item to javafx.fxml;
    opens common.models.auction to javafx.fxml;

    // Xuất các gói để sử dụng trong dự án
    exports common.models.user;
    exports common.models.entity;
    exports common.models.item;
    exports common.models.auction;
    exports server.manager;
    exports server.repository;
    opens server.repository to javafx.fxml;
}