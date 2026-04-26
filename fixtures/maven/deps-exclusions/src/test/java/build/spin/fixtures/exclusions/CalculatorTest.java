package build.spin.fixtures.exclusions;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculatorTest {

    @Test
    void add() {
        assertEquals(5, new Calculator().add(2, 3));
    }

    @Test
    void jacksonAnnotationsIsExcluded() {
        // jackson-annotations was excluded from jackson-databind's transitive deps
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.fasterxml.jackson.annotation.JsonProperty"));
    }
}
