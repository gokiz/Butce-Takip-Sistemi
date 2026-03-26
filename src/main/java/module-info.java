module com.budget {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.apache.poi.poi;       // Excel için gerekli
    requires org.apache.poi.ooxml;     // Excel için gerekli

    opens com.budget to javafx.fxml;
    exports com.budget;
}