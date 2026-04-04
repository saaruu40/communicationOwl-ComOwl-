
module com.example {
    requires transitive javafx.controls;
    requires transitive javafx.fxml;
    requires transitive javafx.graphics;
    requires transitive javafx.swing;

    requires java.desktop;
    requires transitive java.sql;
    requires org.xerial.sqlitejdbc;
    requires webcam.capture;
    requires javafx.media;

    opens com.example to javafx.fxml;
    exports com.example;


    exports com.codes.auth;
    exports com.codes.database;
    exports com.codes.model;
    exports com.codes.server;
    exports com.codes.util;
}
