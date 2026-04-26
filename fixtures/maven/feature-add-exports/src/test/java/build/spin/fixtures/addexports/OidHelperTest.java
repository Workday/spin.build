package build.spin.fixtures.addexports;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OidHelperTest {

    @Test
    void sha256OidIsNotNull() {
        assertNotNull(OidHelper.sha256OidString());
    }
}
