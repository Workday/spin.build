package build.spin.fixtures.ap.withdeps;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConfigViewTest {

    @Test
    void viewHasFields() {
        assertFalse(ConfigView.fields().isEmpty());
    }
}
