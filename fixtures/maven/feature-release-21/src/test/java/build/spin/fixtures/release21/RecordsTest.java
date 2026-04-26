package build.spin.fixtures.release21;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordsTest {

    @Test
    void pointDistance() {
        var p = new Records.Point(3, 4);
        assertEquals(5.0, p.distance());
    }
}
