package build.spin.module.java;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
    void isJvmNoise_doesNotMatchSpawnAgentPrefix() {
        assertThat(ErrorCapture.isJvmNoise("[SpawnAgent:41] Connecting to spawn://...")).isFalse();
    }

    @Test
    void isSpawnAgentOutput_matchesSpawnAgentPrefix() {
        assertThat(ErrorCapture.isSpawnAgentOutput("[SpawnAgent:41] Connecting to spawn://...")).isTrue();
    }

    @Test
    void isSpawnAgentOutput_doesNotMatchOtherLines() {
        assertThat(ErrorCapture.isSpawnAgentOutput("WARNING: something")).isFalse();
    }

    @Test
    void isJvmNoise_doesNotMatchRealError() {
        assertThat(ErrorCapture.isJvmNoise("Exception in thread \"main\" java.lang.NullPointerException")).isFalse();
    }

    // -------------------------------------------------------------------------
    // isCdsDumpNoise
    // -------------------------------------------------------------------------

    @Test
    void isCdsDumpNoise_matchesCdsWarningTag() {
        assertThat(ErrorCapture.isCdsDumpNoise(
            "[0.123s][warning][cds] Skipping foo/Bar: JFR event class"))
            .isTrue();
    }

    @Test
    void isCdsDumpNoise_matchesGraalJvmciDisabledWarning() {
        assertThat(ErrorCapture.isCdsDumpNoise(
            "OpenJDK 64-Bit Server VM warning: JVMCI Compiler disabled due to -Xint"))
            .isTrue();
    }

    @Test
    void isCdsDumpNoise_doesNotMatchRealDumpFailure() {
        assertThat(ErrorCapture.isCdsDumpNoise(
            "Error occurred during initialization of VM")).isFalse();
        assertThat(ErrorCapture.isCdsDumpNoise(
            "Exception in thread \"main\" java.lang.module.FindException: Module foo not found"))
            .isFalse();
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

    // -------------------------------------------------------------------------
    // selectOutput
    // -------------------------------------------------------------------------

    @Test
    void selectOutput_returnsStderrWhenNonEmpty() {
        assertThat(ErrorCapture.selectOutput("Error: bad arg", Stream.of("stdout line")))
            .isEqualTo("Error: bad arg");
    }

    @Test
    void selectOutput_fallsBackToStdoutWhenStderrEmpty() {
        assertThat(ErrorCapture.selectOutput("", Stream.of("Error: Module foo not found")))
            .isEqualTo("Error: Module foo not found");
    }

    @Test
    void selectOutput_joinsMultipleStdoutLinesWithNewline() {
        assertThat(ErrorCapture.selectOutput("", Stream.of("line one", "line two", "line three")))
            .isEqualTo("line one\nline two\nline three");
    }

    @Test
    void selectOutput_returnsEmptyWhenBothEmpty() {
        assertThat(ErrorCapture.selectOutput("", Stream.empty())).isEmpty();
    }

    // -------------------------------------------------------------------------
    // junitFailures
    // -------------------------------------------------------------------------

    // a verbatim stdout transcript from a real `spin clean build` run whose spin-engine-tests
    // JUnit task failed: the ConsoleLauncher tree and pass/fail summary interleaved with the
    // telemetry the tests under it printed to stdout.
    private static String consoleLauncherStdout() throws IOException {
        try (var in = ErrorCaptureTests.class.getResourceAsStream("console-launcher-stdout.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void junitFailures_extractsOnlyTheFailuresSection() throws IOException {
        assertThat(ErrorCapture.junitFailures(consoleLauncherStdout())).isEqualTo(String.join("\n",
            "Failures (1):",
            "  JUnit Jupiter:DefaultEngineResourceScopingTests:shouldHonorASpinIgnoreDefinedInANonRootProjectNotJustTheWorkspaceRoot(Path)",
            "    MethodSource [className = 'build.spin.engine.tests.DefaultEngineResourceScopingTests', methodName = 'shouldHonorASpinIgnoreDefinedInANonRootProjectNotJustTheWorkspaceRoot', methodParameterTypes = 'java.nio.file.Path']",
            "    => java.util.NoSuchElementException: No value present",
            "       java.base/java.util.Optional.orElseThrow(Optional.java:377)",
            "       build.spin.engine.tests.test@0.4.1-SNAPSHOT/build.spin.engine.tests.DefaultEngineResourceScopingTests.shouldHonorASpinIgnoreDefinedInANonRootProjectNotJustTheWorkspaceRoot(DefaultEngineResourceScopingTests.java:91)"));
    }

    @Test
    void junitFailures_dropsPrecedingTreeAndTestTelemetry() throws IOException {
        assertThat(ErrorCapture.junitFailures(consoleLauncherStdout()))
            .doesNotContain("JUnit Jupiter ✔")
            .doesNotContain("WorkspaceDiscovery")
            .doesNotContain("Test run finished")
            .doesNotContain("containers found");
    }

    @Test
    void junitFailures_returnsInputUnchangedWhenNoFailuresSection() {
        final String stdout = "some tool output\nwith no junit failure summary";
        assertThat(ErrorCapture.junitFailures(stdout)).isEqualTo(stdout);
    }

    @Test
    void junitFailures_keepsWholeSectionWhenNoTrailingSummary() {
        final String stdout = String.join("\n",
            "Failures (1):",
            "  JUnit Jupiter:SomeTest:fails()",
            "    => java.lang.AssertionError: expected true");
        assertThat(ErrorCapture.junitFailures(stdout)).isEqualTo(stdout);
    }

    @Test
    void junitFailures_keepsSeparatorBlankLinesButCollapsesRuns() {
        final String stdout = String.join("\n",
            "Failures (2):",
            "  first",
            "",
            "",
            "  second",
            "",
            "Test run finished after 1 ms");
        assertThat(ErrorCapture.junitFailures(stdout))
            .isEqualTo("Failures (2):\n  first\n\n  second");
    }

    @Test
    void junitFailures_ignoresLinesThatMerelyStartWithFailures() {
        final String stdout = String.join("\n",
            "Failures (the flaky ones) are being retried",
            "  some telemetry",
            "Failures (1):",
            "  JUnit Jupiter:SomeTest:fails()");
        assertThat(ErrorCapture.junitFailures(stdout))
            .isEqualTo("Failures (1):\n  JUnit Jupiter:SomeTest:fails()");
    }

    // -------------------------------------------------------------------------
    // junitFailureReport
    // -------------------------------------------------------------------------

    @Test
    void junitFailureReport_appendsCapturedStderrAfterTheFailureSummary() throws IOException {
        assertThat(ErrorCapture.junitFailureReport("boom on stderr", consoleLauncherStdout()))
            .startsWith("Failures (1):")
            .endsWith("\n\nboom on stderr");
    }

    @Test
    void junitFailureReport_omitsStderrWhenEmpty() throws IOException {
        assertThat(ErrorCapture.junitFailureReport("", consoleLauncherStdout()))
            .isEqualTo(ErrorCapture.junitFailures(consoleLauncherStdout()));
    }

    @Test
    void junitFailureReport_returnsStderrAloneWhenStdoutHasNoFailuresSection() {
        final String stdout = String.join("\n",
            "Thanks for using JUnit!",
            "some telemetry line",
            "and a big console tree");
        assertThat(ErrorCapture.junitFailureReport("Error: unknown option '--nope'", stdout))
            .isEqualTo("Error: unknown option '--nope'");
    }

    @Test
    void junitFailureReport_fallsBackToStdoutWhenStderrEmptyAndNoFailuresSection() {
        assertThat(ErrorCapture.junitFailureReport("", "raw console output, no summary"))
            .isEqualTo("raw console output, no summary");
    }

    @Test
    void junitFailureReport_isEmptyWhenBothPartsEmpty() {
        assertThat(ErrorCapture.junitFailureReport("", "")).isEmpty();
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
