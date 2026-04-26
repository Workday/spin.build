package build.spin.fixtures.multiNamed.impl;

import build.spin.fixtures.multiNamed.api.Arithmetic;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculatorTest {

    private final Arithmetic calc = new Calculator();

    @Test
    void add() {
        assertEquals(5, calc.add(2, 3));
    }

    @Test
    void multiply() {
        assertEquals(6, calc.multiply(2, 3));
    }
}
