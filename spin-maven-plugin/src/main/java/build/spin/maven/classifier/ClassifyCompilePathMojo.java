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
 * Classify the project's <em>compile</em> classpath and write the result to an
 * {@code @argfile} that {@code maven-compiler-plugin} can consume via
 * {@code <compilerArgument>@${project.build.directory}/spin-classify-compile.args</compilerArgument>}
 * (with {@code <useModulePath>false</useModulePath>} to disable the plugin's own module-path
 * classification so our curated path isn't double-computed).
 */
@Mojo(
    name = "classify-compile-path",
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    threadSafe = true)
public class ClassifyCompilePathMojo extends AbstractClassifyPathMojo {

    @Parameter(defaultValue = "${project.build.directory}/spin-classify-compile.args")
    private File argfile;

    @Parameter(defaultValue = "${project.basedir}/src/main/java/module-info.java")
    private File moduleInfoSource;

    @Override
    protected List<Path> getCandidates() throws DependencyResolutionRequiredException {
        return project.getCompileClasspathElements().stream()
            .map(Path::of)
            .toList();
    }

    @Override
    protected Optional<Path> getSourceModuleInfo() {
        return moduleInfoSource != null && moduleInfoSource.exists()
            ? Optional.of(moduleInfoSource.toPath())
            : Optional.empty();
    }

    @Override
    protected Path getArgfile() {
        return argfile.toPath();
    }

    @Override
    protected String getPropertyPrefix() {
        return "spin.classify.compile";
    }
}
