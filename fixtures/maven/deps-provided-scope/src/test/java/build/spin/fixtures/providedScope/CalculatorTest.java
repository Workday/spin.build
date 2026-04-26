package build.spin.fixtures.providedScope;

import jakarta.servlet.http.HttpServlet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CalculatorTest {

    @Test
    void add() {
        assertEquals(5, new Calculator().add(2, 3));
    }

    @Test
    void providedDepIsAvailableAtCompileAndTestTime() {
        // provided scope: available to compile against and run tests, but absent from the packaged jar
        assertNotNull(HttpServlet.class.getName());
    }
}
