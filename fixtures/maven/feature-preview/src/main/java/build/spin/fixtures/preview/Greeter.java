package build.spin.fixtures.preview;

public class Greeter {

    public String greet(Object obj) {
        // Uses pattern matching in switch — a preview feature in Java 21, standard in 21+
        return switch (obj) {
            case Integer i -> "number: " + i;
            case String s  -> "string: " + s;
            default        -> "other: " + obj;
        };
    }
}
