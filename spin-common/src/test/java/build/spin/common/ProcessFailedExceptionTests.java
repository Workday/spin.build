package build.spin.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessFailedExceptionTests {

    @Test
    void messageAndOutputAreStoredIndependently() {
        final var e = new ProcessFailedException("javac failed", "error: cannot find symbol");

        assertThat(e.getMessage()).isEqualTo("javac failed");
        assertThat(e.output()).isEqualTo("error: cannot find symbol");
    }

    @Test
    void outputCanBeEmpty() {
        final var e = new ProcessFailedException("jlink failed", "");

        assertThat(e.output()).isEmpty();
    }

    @Test
    void isARuntimeException() {
        assertThat(new ProcessFailedException("msg", "out"))
            .isInstanceOf(RuntimeException.class);
    }
}
