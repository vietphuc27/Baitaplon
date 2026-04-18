module view.btl {
    requires javafx.controls;
    requires javafx.fxml;


    opens models.user to javafx.fxml;
    opens models.entity to javafx.fxml;
    opens models.item to javafx.fxml;
    opens models.auction to javafx.fxml;

    // Xuất các gói để sử dụng trong dự án
    exports models.user;
    exports models.entity;
    exports models.item;
    exports models.auction;
    exports manager;
}