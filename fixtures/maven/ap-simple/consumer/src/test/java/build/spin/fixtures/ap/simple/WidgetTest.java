package build.spin.fixtures.ap.simple;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WidgetTest {

    @Test
    void widgetName() {
        assertEquals("widget", new Widget().name());
    }

    @Test
    void generatedLoggerExists() throws Exception {
        // Verify the AP-generated class is on the classpath
        Class.forName("build.spin.fixtures.ap.simple.WidgetLogger");
    }
}
