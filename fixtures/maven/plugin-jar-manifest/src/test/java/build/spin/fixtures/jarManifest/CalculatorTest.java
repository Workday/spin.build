package build.spin.fixtures.jarManifest;

import org.junit.jupiter.api.Test;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CalculatorTest {

    @Test
    void calculatorWorks() {
        assertEquals(5, new Calculator().add(2, 3));
    }

    @Test
    void manifestContainsCustomEntries() throws Exception {
        String jarPath = System.getProperty("project.build.directory")
            + "/" + System.getProperty("project.build.finalName") + ".jar";
        try (var jar = new JarFile(jarPath)) {
            var attrs = jar.getManifest().getMainAttributes();
            assertEquals("spin", attrs.getValue("Build-Tool"));
            assertNotNull(attrs.getValue("Build-Jdk"));
        }
    }
}
