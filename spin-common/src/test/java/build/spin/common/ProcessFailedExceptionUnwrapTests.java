package build.spin.common;

/*-
 * #%L
 * Spin Common Library
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessFailedExceptionUnwrapTests {

    @Test
    void unwrap_returnsOriginalWhenNoProcessFailedExceptionInChain() {
        final var root = new RuntimeException("plain failure");
        assertThat(ProcessFailedException.unwrap(root)).isSameAs(root);
    }

    @Test
    void unwrap_returnsProcessFailedExceptionWhenItIsTheDirectCause() {
        final var pfe = new ProcessFailedException("javac failed", "error: cannot find symbol");
        final var wrapper = new RuntimeException("JUnit Failure", pfe);
        assertThat(ProcessFailedException.unwrap(wrapper)).isSameAs(pfe);
    }

    @Test
    void unwrap_returnsProcessFailedExceptionBuriedDeepInChain() {
        final var pfe = new ProcessFailedException("jlink failed", "");
        final var mid = new RuntimeException("mid", pfe);
        final var outer = new RuntimeException("outer", mid);
        assertThat(ProcessFailedException.unwrap(outer)).isSameAs(pfe);
    }

    @Test
    void unwrap_returnsProcessFailedExceptionItselfWhenPassedDirectly() {
        final var pfe = new ProcessFailedException("javac failed", "error: cannot find symbol");
        assertThat(ProcessFailedException.unwrap(pfe)).isSameAs(pfe);
    }
}
