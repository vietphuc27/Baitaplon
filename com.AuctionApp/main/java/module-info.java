module view.btl {
    requires javafx.controls;
    requires javafx.fxml;


    opens user to javafx.fxml;
    opens entity to javafx.fxml;
    opens item to javafx.fxml;
    opens auction to javafx.fxml;

    // Xuất các gói để sử dụng trong dự án
    exports user;
    exports entity;
    exports item;
    exports auction;
    exports manager;
}