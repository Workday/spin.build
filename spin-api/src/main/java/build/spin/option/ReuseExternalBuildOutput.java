package build.spin.option;

/*-
 * #%L
 * Spin API
 * %%
 * Copyright (C) 2026 Workday, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import build.base.commandline.CommandLine;
import build.base.configuration.Default;
import build.base.configuration.Option;

/**
 * An {@link Option} to define whether already-built output from another build tool (Maven's
 * {@code target/classes}, Gradle's {@code classes/java/main}) may be trusted as equivalent to spin's
 * own {@code .build/} output -- for both deciding whether a {@link build.spin.Task} can skip redoing
 * its work, and deciding whether a workspace sibling needs to be ordered ahead of a dependent compile.
 *
 * <p>Disabled by default: spin only ever recognizes its own prior {@code .build/} output, so
 * {@code spin build}/{@code spin jlink} against a project that happens to already have Maven or Gradle
 * output on disk still compiles it with spin, matching spin's long-standing independence from other
 * build tools. Enabling this is a deliberate opt-in for callers (e.g. lang.build's fast-iteration loop,
 * spin's own {@code spin1-build-spin2} bootstrap step) that want to avoid redundant recompilation of
 * output another tool already produced.
 */
public enum ReuseExternalBuildOutput
    implements Option {

    /**
     * Already-built Maven/Gradle output may be reused in place of spin's own {@code .build/} output.
     */
    ENABLED,

    /**
     * Only spin's own prior {@code .build/} output is ever reused; Maven/Gradle output is ignored.
     */
    @Default
    DISABLED;

    /**
     * Creates a {@link ReuseExternalBuildOutput} based on the {@link CommandLine} {@link Option}.
     *
     * @return a {@link ReuseExternalBuildOutput}
     */
    @CommandLine.Prefix("--reuse-external-build-output")
    @CommandLine.Description("Allow already-built Maven/Gradle output to be reused instead of recompiling with spin")
    public static ReuseExternalBuildOutput enabled() {
        return ReuseExternalBuildOutput.ENABLED;
    }
}
