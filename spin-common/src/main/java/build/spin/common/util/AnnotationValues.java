package build.spin.common.util;

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

import build.codemodel.foundation.usage.AnnotationTypeUsage;
import build.codemodel.foundation.usage.AnnotationValue;

import java.util.Optional;

/**
 * Helpers for extracting typed values from {@link AnnotationTypeUsage}s.
 *
 * <p>TODO: port to codemodel (AnnotationValue / TypeUsages) once codemodel releases settle.
 */
public final class AnnotationValues {

    private AnnotationValues() {}

    /**
     * Returns the first {@link AnnotationValue} of the given annotation whose value is a
     * {@link AnnotationValue.Value.Literal} of the requested type.
     */
    public static <T> Optional<T> firstLiteral(final AnnotationTypeUsage annotation,
                                               final Class<T> type) {
        return annotation.values()
            .map(AnnotationValue::value)
            .filter(AnnotationValue.Value.Literal.class::isInstance)
            .map(AnnotationValue.Value.Literal.class::cast)
            .map(AnnotationValue.Value.Literal::value)
            .filter(type::isInstance)
            .map(type::cast)
            .findFirst();
    }

    /**
     * Returns the first {@link AnnotationValue} of the given annotation whose value is a
     * {@link AnnotationValue.Value.ClassRef}, loaded via the thread-context classloader.
     */
    public static Optional<Class<?>> firstClassRef(final AnnotationTypeUsage annotation) {
        return annotation.values()
            .map(AnnotationValue::value)
            .filter(AnnotationValue.Value.ClassRef.class::isInstance)
            .map(AnnotationValue.Value.ClassRef.class::cast)
            .findFirst()
            .flatMap(ref -> {
                try {
                    return Optional.of(Class.forName(ref.typeName().binaryName(), true,
                        Thread.currentThread().getContextClassLoader()));
                } catch (final ClassNotFoundException e) {
                    return Optional.empty();
                }
            });
    }
}
