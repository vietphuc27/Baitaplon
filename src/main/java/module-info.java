module view.btl {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive java.sql;
    requires javafx.graphics;
    requires gson;

// requires com.mysql.cj;

    exports com.example.btl_n3;
    opens common.models.user to javafx.fxml;
    opens common.models.entity to javafx.fxml;
    opens common.models.item to javafx.fxml;
    opens common.models.auction to javafx.fxml;
    opens client.controller to javafx.fxml;

    // Xuáº¥t cĂ¡c gĂ³i Ä‘á»ƒ sá»­ dá»¥ng trong dá»± Ă¡n
    exports common.models.user;
    exports common.models.entity;
    exports common.models.item;
    exports common.models.auction;
    exports server.manager;
    exports server.repository;
    opens server.repository to javafx.fxml;
    exports server.repository.dao;
    opens server.repository.dao to javafx.fxml;
    exports server.config;
}
