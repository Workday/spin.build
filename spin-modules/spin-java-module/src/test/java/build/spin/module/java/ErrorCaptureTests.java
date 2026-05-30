package build.spin.module.java;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCaptureTests {

    @Test
    void output_isEmptyBeforeAnyAppends() {
        assertThat(new ErrorCapture().output()).isEmpty();
    }

    @Test
    void append_storesFirstLine() {
        final var capture = new ErrorCapture();
        capture.append("error: cannot find symbol");
        assertThat(capture.output()).isEqualTo("error: cannot find symbol");
    }

    @Test
    void append_joinsSubsequentLinesWithNewline() {
        final var capture = new ErrorCapture();
        capture.append("error: cannot find symbol");
        capture.append("  symbol: class Nothing");
        capture.append("  location: class Broken");
        assertThat(capture.output()).isEqualTo(
            "error: cannot find symbol\n  symbol: class Nothing\n  location: class Broken");
    }

    @Test
    void append_doesNotPrependLeadingNewline() {
        final var capture = new ErrorCapture();
        capture.append("first");
        assertThat(capture.output()).doesNotStartWith("\n");
    }

    @Test
    void subscriber_capturesLinesInOrder() {
        final var capture = new ErrorCapture();
        final var sub = capture.subscriber(line -> {
        });

        sub.get().onNext("line one");
        sub.get().onNext("line two");

        assertThat(capture.output()).isEqualTo("line one\nline two");
    }

    @Test
    void subscriber_forwardsLinesToLog() {
        final var capture = new ErrorCapture();
        final List<String> logged = new ArrayList<>();
        final var sub = capture.subscriber(line -> logged.add(line));

        sub.get().onNext("error: something went wrong");

        assertThat(logged).containsExactly("error: something went wrong");
    }

    @Test
    void subscriber_andAppend_accumulateIntoSameBuffer() {
        final var capture = new ErrorCapture();
        capture.append("block one");
        capture.subscriber(line -> {
        }).get().onNext("block two");
        assertThat(capture.output()).isEqualTo("block one\nblock two");
    }
}
