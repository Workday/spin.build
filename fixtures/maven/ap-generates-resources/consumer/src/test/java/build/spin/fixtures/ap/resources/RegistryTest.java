package build.spin.fixtures.ap.resources;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryTest {

    @Test
    void registryContainsBothServices() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/META-INF/registry.txt")) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains("build.spin.fixtures.ap.resources.ServiceA"));
            assertTrue(content.contains("build.spin.fixtures.ap.resources.ServiceB"));
        }
    }
}
