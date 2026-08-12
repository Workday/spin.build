package build.spin;

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

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpinURI}.
 */
class SpinURITests {

    @Test
    void qualifiesTheSchemeWithTheSpinPrefix() {
        final URI uri = SpinURI.create("task", "workspace/project/Plugin.task");

        assertThat(uri.getScheme()).isEqualTo("spin-task");
    }

    @Test
    void preservesThePathAfterTheSchemePrefix() {
        final URI uri = SpinURI.create("project", "workspace/project");

        assertThat(uri.getSchemeSpecificPart()).contains("workspace/project");
    }

    @Test
    void distinguishesDifferentSchemesSharingTheSamePath() {
        final URI taskUri = SpinURI.create("task", "same-path");
        final URI programUri = SpinURI.create("program", "same-path");

        assertThat(taskUri.getScheme()).isNotEqualTo(programUri.getScheme());
    }
}
