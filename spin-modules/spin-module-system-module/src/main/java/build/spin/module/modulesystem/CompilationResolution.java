package build.spin.module.modulesystem;

/*-
 * #%L
 * Spin Module System Module
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

import build.base.io.PathSet;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The result of source-graph dependency resolution: a partition of all candidate jars and
 * compiled-class directories into those that belong on {@code --module-path} and those that
 * belong on {@code -classpath}.
 *
 * <p>Produced by {@code AbstractDetectResolution} and {@code AbstractDetectTestResolution}
 * and consumed directly by compile, javadoc, and test tasks via {@code @From}.
 */
public record CompilationResolution(List<Path> modulePath, List<Path> classPath) {

    public CompilationResolution {
        modulePath = List.copyOf(modulePath);
        classPath  = List.copyOf(classPath);
    }

    /**
     * Returns a {@link CompilationResolution} with each entry of {@code paths} appended to
     * {@link #modulePath()} (if it satisfies {@code isNamedModule}) or {@link #classPath()}
     * (otherwise), or {@code this} unchanged if {@code paths} is empty.
     *
     * <p>Callers with an already-known-correct candidate on hand (e.g. a project's own compiled
     * output, guaranteed fresh some other way) still need this split -- a named-module candidate
     * placed on the classpath instead of the module-path is invisible to a modular {@code -m}
     * invocation ({@code java.lang.module.FindException: Module ... not found}), same as any other
     * candidate {@code ModuleGraphClassifier} classifies.
     *
     * @param paths additional resolution entries to merge in
     * @param isNamedModule tests whether a given entry is a real (named) JPMS module
     * @return the merged {@link CompilationResolution}
     */
    public CompilationResolution withAdditional(final PathSet paths, final Predicate<Path> isNamedModule) {
        if (paths.isEmpty()) {
            return this;
        }
        final List<Path> extraModulePath = paths.stream().filter(isNamedModule).toList();
        final List<Path> extraClassPath = paths.stream().filter(isNamedModule.negate()).toList();
        return new CompilationResolution(
            Stream.concat(this.modulePath.stream(), extraModulePath.stream()).toList(),
            Stream.concat(this.classPath.stream(), extraClassPath.stream()).toList());
    }
}
