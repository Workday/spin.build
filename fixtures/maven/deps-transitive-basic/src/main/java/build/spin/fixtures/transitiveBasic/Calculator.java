package build.spin.fixtures.transitiveBasic;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Calculator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public int add(int a, int b) {
        return a + b;
    }

    public String toJson(int value) throws Exception {
        return MAPPER.writeValueAsString(value);
    }
}
