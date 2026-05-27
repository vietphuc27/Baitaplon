module view.btl {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive java.sql;
    requires javafx.graphics;
    requires com.google.gson;
    requires jjwt.api;
    requires cloudinary.http44;
    requires cloudinary.core;
    // requires com.mysql.cj;

    exports com.example.btl_n3;
    opens com.example.btl_n3;

    opens common.models.user to javafx.fxml;
    opens common.models.entity to javafx.fxml;
    opens common.models.item to javafx.fxml;
    opens common.models.auction to javafx.fxml;
    opens client.controller;
    opens client.network;
    opens client.application;

    // Export packages used by the application
    exports common.models.user;
    exports common.models.entity;
    exports common.models.item;
    exports common.models.auction;
    exports common.models;

    // Export & Open util, factory, and exception packages for tests
    exports common.utils;
    opens common.utils;

    exports common.exceptions;
    opens common.exceptions;

    exports common.itemfactory;
    opens common.itemfactory;

    exports common.userfactory;
    opens common.userfactory;

    // Open package root for test reflection when running on module-path (IDE/JUnit)
    opens common.models;

    exports server.manager;
    opens server.manager;

    exports server.repository;
    opens server.repository;

    exports server.repository.dao;

    opens server.repository.dao to javafx.fxml;

    exports server.service;
    opens server.service;
    exports server.config;

    // Open packages used by reflective test runners (IDE/JUnit on module-path)
    exports server.network;
    opens server.network;

    exports server.application;
    opens server.application;

    exports server.util;
    opens server.util;
}
