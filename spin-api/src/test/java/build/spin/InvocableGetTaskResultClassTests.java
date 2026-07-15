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
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Invocable#getTaskResultClass()}, in particular that a {@link Task} declaring a
 * generic (parameterized) result type — e.g. {@code Task<Set<Path>>}, as used by a {@code jlink} task
 * producing one runtime image per target platform — resolves to its raw {@link Class} rather than
 * being silently dropped in favor of {@code void}.
 *
 * @author reed.vonredwitz
 * @since Jul-2026
 */
class InvocableGetTaskResultClassTests {

    private static class SimpleResultTask
        implements Task<Path> {
    }

    private static class GenericResultTask
        implements Task<Set<Path>> {
    }

    private static class AbstractGenericResultTask
        implements Task<Set<Path>> {
    }

    private static class ConcreteSubclassOfGenericResultTask
        extends AbstractGenericResultTask {
    }

    @SuppressWarnings("unchecked")
    private static <T> Invocable<T> invocableFor(final Class<? extends Task<T>> taskClass) {
        return new Invocable<>() {
            @Override
            public URI getURI() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Project getProject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Plugin getPlugin() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Class<? extends Task<T>> getTaskClass() {
                return taskClass;
            }
        };
    }

    @Test
    void resolvesSimpleClassResultType() {
        final var invocable = invocableFor(SimpleResultTask.class);
        assertThat(invocable.getTaskResultClass()).contains(Path.class);
    }

    @Test
    void resolvesGenericResultTypeToItsRawClass() {
        final var invocable = invocableFor(GenericResultTask.class);
        assertThat(invocable.getTaskResultClass()).contains(Set.class);
    }

    @Test
    void resolvesGenericResultTypeInheritedFromAnAbstractTaskSuperclass() {
        final var invocable = invocableFor(ConcreteSubclassOfGenericResultTask.class);
        assertThat(invocable.getTaskResultClass()).contains(Set.class);
    }
}
