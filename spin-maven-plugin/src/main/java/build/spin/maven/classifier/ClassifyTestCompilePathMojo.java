package build.spin.maven.classifier;

/*-
 * #%L
 * Spin Classifier Maven Plugin
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

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Classify the project's <em>test</em> classpath and write the result to an {@code @argfile}
 * suitable for consumption by both {@code maven-compiler-plugin}'s test-compile goal (via
 * {@code <testCompilerArgument>}) and {@code maven-surefire-plugin}'s {@code <argLine>}. The
 * same argfile drives both tools because the javac and java launchers use the same
 * {@code @argfile} format.
 *
 * <p>Seed strategy (see {@link AbstractClassifyPathMojo#computeSeed}) first looks for a
 * test-specific {@code src/test/java/module-info.java}, falling back to the main
 * {@code src/main/java/module-info.java}, and finally falling back to the project's direct
 * dependency module names. Test-only sibling modules with no module-info at all
 * (e.g. {@code spin-java-module-tests}) therefore get their seed from the direct-dep fallback.
 */
@Mojo(
    name = "classify-test-compile-path",
    defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES,
    requiresDependencyResolution = ResolutionScope.TEST,
    threadSafe = true)
public class ClassifyTestCompilePathMojo extends AbstractClassifyPathMojo {

    @Parameter(defaultValue = "${project.build.directory}/spin-classify-test.args")
    private File argfile;

    @Parameter(defaultValue = "${project.basedir}/src/test/java/module-info.java")
    private File testModuleInfoSource;

    @Parameter(defaultValue = "${project.basedir}/src/main/java/module-info.java")
    private File mainModuleInfoSource;

    @Override
    protected List<Path> getCandidates() throws DependencyResolutionRequiredException {
        return project.getTestClasspathElements().stream()
            .map(Path::of)
            .toList();
    }

    @Override
    protected Optional<Path> getSourceModuleInfo() {
        if (testModuleInfoSource != null && testModuleInfoSource.exists()) {
            return Optional.of(testModuleInfoSource.toPath());
        }
        if (mainModuleInfoSource != null && mainModuleInfoSource.exists()) {
            return Optional.of(mainModuleInfoSource.toPath());
        }
        return Optional.empty();
    }

    @Override
    protected Path getArgfile() {
        return argfile.toPath();
    }

    @Override
    protected String getPropertyPrefix() {
        return "spin.classify.test";
    }
}
