package build.spin.fixtures.transitiveBasic;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculatorTest {

    private final Calculator calc = new Calculator();

    @Test
    void add() {
        assertEquals(5, calc.add(2, 3));
    }

    @Test
    void jacksonCoreIsTransitivelyAvailable() throws Exception {
        // jackson-core is not declared directly — it arrives via jackson-databind
        assertEquals("42", calc.toJson(42));
    }
}
