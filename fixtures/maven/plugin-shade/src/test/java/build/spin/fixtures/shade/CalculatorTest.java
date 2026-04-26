package build.spin.fixtures.shade;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculatorTest {

    private final Calculator calc = new Calculator();

    @Test
    void add() {
        assertEquals(5, calc.add(2, 3));
    }

    @Test
    void multiply() {
        assertEquals(6, calc.multiply(2, 3));
    }
}
