package build.spin.fixtures.bom;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CalculatorTest {

    @Test
    void add() {
        assertEquals(5, new Calculator().add(2, 3));
    }

    @Test
    void bomManagedDepsAreResolved() throws Exception {
        // Both jackson-databind and jackson-core declared without versions via BOM
        ObjectMapper mapper = new ObjectMapper();
        assertNotNull(mapper.writeValueAsString(42));
        assertNotNull(new JsonFactory());
    }
}
