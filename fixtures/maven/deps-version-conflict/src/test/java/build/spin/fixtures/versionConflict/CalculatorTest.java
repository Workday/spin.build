package build.spin.fixtures.versionConflict;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorTest {

    @Test
    void add() {
        assertEquals(5, new Calculator().add(2, 3));
    }

    @Test
    void guavaIsOnClasspath() {
        // Confirms whichever guava version was resolved is usable
        ImmutableList<Integer> list = ImmutableList.of(1, 2, 3);
        assertTrue(list.contains(2));
    }
}
