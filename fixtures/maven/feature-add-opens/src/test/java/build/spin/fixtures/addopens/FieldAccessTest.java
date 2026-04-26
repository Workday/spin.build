package build.spin.fixtures.addopens;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldAccessTest {

    @Test
    void hashCodeMatchesReflective() throws Exception {
        String s = "hello";
        assertEquals(s.hashCode(), FieldAccess.stringHashCode(s));
    }
}
