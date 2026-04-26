package build.spin.fixtures.preview;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GreeterTest {

    @Test
    void greetInteger() {
        assertEquals("number: 42", new Greeter().greet(42));
    }

    @Test
    void greetString() {
        assertEquals("string: hello", new Greeter().greet("hello"));
    }
}
