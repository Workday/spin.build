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

    // -------------------------------------------------------------------------
    // isJavacWarning
    // -------------------------------------------------------------------------

    @Test
    void isJavacWarning_matchesWarningDiagnostic() {
        assertThat(ErrorCapture.isJavacWarning("Foo.java:10: warning: [deprecation] bar() is deprecated")).isTrue();
    }

    @Test
    void isJavacWarning_matchesNoteLine() {
        assertThat(ErrorCapture.isJavacWarning("Note: Foo.java uses unchecked or unsafe operations.")).isTrue();
    }

    @Test
    void isJavacWarning_doesNotMatchError() {
        assertThat(ErrorCapture.isJavacWarning("Foo.java:10: error: cannot find symbol")).isFalse();
    }

    // -------------------------------------------------------------------------
    // isJvmNoise
    // -------------------------------------------------------------------------

    @Test
    void isJvmNoise_matchesBlankLine() {
        assertThat(ErrorCapture.isJvmNoise("")).isTrue();
        assertThat(ErrorCapture.isJvmNoise("   ")).isTrue();
    }

    @Test
    void isJvmNoise_matchesWarningPrefix() {
        assertThat(ErrorCapture.isJvmNoise("WARNING: Using incubator modules: jdk.incubator.vector")).isTrue();
    }

    @Test
    void isJvmNoise_matchesSpawnAgentPrefix() {
        assertThat(ErrorCapture.isJvmNoise("[SpawnAgent:] attached successfully")).isTrue();
    }

    @Test
    void isJvmNoise_doesNotMatchRealError() {
        assertThat(ErrorCapture.isJvmNoise("Exception in thread \"main\" java.lang.NullPointerException")).isFalse();
    }

    // -------------------------------------------------------------------------
    // triageSubscriber
    // -------------------------------------------------------------------------

    @Test
    void triageSubscriber_routesNoiseToWarnAndSkipsCapture() {
        final var capture = new ErrorCapture();
        final List<String> warned = new ArrayList<>();
        final List<String> errored = new ArrayList<>();
        final var sub = capture.triageSubscriber(ErrorCapture::isJvmNoise, warned::add, errored::add);

        sub.get().onNext("WARNING: something incubating");

        assertThat(warned).containsExactly("WARNING: something incubating");
        assertThat(errored).isEmpty();
        assertThat(capture.output()).isEmpty();
    }

    @Test
    void triageSubscriber_routesRealErrorToErrorAndCaptures() {
        final var capture = new ErrorCapture();
        final List<String> warned = new ArrayList<>();
        final List<String> errored = new ArrayList<>();
        final var sub = capture.triageSubscriber(ErrorCapture::isJvmNoise, warned::add, errored::add);

        sub.get().onNext("Exception in thread \"main\" java.lang.NullPointerException");

        assertThat(errored).containsExactly("Exception in thread \"main\" java.lang.NullPointerException");
        assertThat(warned).isEmpty();
        assertThat(capture.output()).isEqualTo("Exception in thread \"main\" java.lang.NullPointerException");
    }

    @Test
    void triageSubscriber_mixedLines_onlyRealErrorsInOutput() {
        final var capture = new ErrorCapture();
        final var sub = capture.triageSubscriber(ErrorCapture::isJvmNoise, line -> {
        }, line -> {
        });

        sub.get().onNext("WARNING: incubating");
        sub.get().onNext("real error line");
        sub.get().onNext("");

        assertThat(capture.output()).isEqualTo("real error line");
    }
}
