
module com.example {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.swing;

    requires java.sql;
    requires org.xerial.sqlitejdbc;

    opens com.example to javafx.fxml;
    exports com.example;


    exports com.codes.auth;
    exports com.codes.database;
    exports com.codes.model;
    exports com.codes.server;
    exports com.codes.util;
}
