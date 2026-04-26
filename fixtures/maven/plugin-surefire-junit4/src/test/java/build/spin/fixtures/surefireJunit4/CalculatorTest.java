package build.spin.fixtures.surefireJunit4;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CalculatorTest {

    private final Calculator calc = new Calculator();

    @Test
    public void add() {
        assertEquals(5, calc.add(2, 3));
    }

    @Test
    public void multiply() {
        assertEquals(6, calc.multiply(2, 3));
    }
}
