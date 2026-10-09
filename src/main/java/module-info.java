module yi.mark {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.base;
    requires java.desktop;

    opens com.yimark to javafx.graphics;
    opens com.yimark.image to javafx.graphics;
    opens com.yimark.watermark to javafx.graphics;
    opens com.yimark.crypto to javafx.graphics;
    opens com.yimark.manifest to javafx.graphics;
    opens com.yimark.protect to javafx.graphics;
    opens com.yimark.verify to javafx.graphics;
    opens com.yimark.trust to javafx.graphics;
    opens com.yimark.util to javafx.graphics;

    exports com.yimark;
}