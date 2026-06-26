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

import build.spin.module.modulesystem.ModuleGraphClassifier;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared logic for the two classifier Mojos. Each subclass supplies the candidate list (compile
 * vs test classpath), the source {@code module-info.java} to seed from, and the output argfile
 * path — this base class handles the rest: seed derivation, the
 * {@link ModuleGraphClassifier#classify ModuleGraphClassifier.classify} call, and argfile
 * serialisation.
 *
 * <h2>Seed derivation</h2>
 *
 * <p>The classifier's tier (a) required-module preference needs a seed set — module names that
 * it should try to keep on the module-path when resolving split-package conflicts. This Mojo
 * picks a seed in the following priority order, stopping at the first non-empty result:
 *
 * <ol>
 *   <li><b>{@code <seedModules>} parameter</b> — explicit list of module names supplied by the
 *       consumer pom. Transitively closed via
 *       {@link ModuleGraphClassifier#closeOverRequires closeOverRequires}. Use this when you
 *       know exactly which modules must be preserved and don't want the plugin to guess.</li>
 *   <li><b>Source {@code module-info.java}</b> — if the project has a source module-info file
 *       for this compilation unit, parse its {@code requires} directives and use them as the
 *       seed (same logic as the compile-time code path in {@code AbstractCompile} /
 *       {@code AbstractJavaDoc}). This is the common case for modules that have their own
 *       {@code module-info.java}.</li>
 *   <li><b>Direct Maven dependencies</b> — fall back to the module names of every artifact
 *       that the project declares as a direct dependency (filtered to those actually on disk
 *       and recognisable as modules). Transitively closed. This is the fallback for test-only
 *       sibling modules like {@code spin-java-module-tests} that have no source
 *       {@code module-info.java} of their own but still need the tier (a) protection so that
 *       modules their deps require (e.g. {@code maven.settings} under Maven 4) stay on the
 *       module path instead of being demoted alongside their split-package siblings.</li>
 *   <li><b>Empty</b> — if none of the above produced a seed, fall through with an empty set.
 *       Tier (a) does not fire and the classifier uses only the proper-over-automatic and
 *       demote-all tiers.</li>
 * </ol>
 *
 * <h2>Argfile format</h2>
 *
 * <p>The output argfile uses the javac / java launcher {@code @argfile} format: one option per
 * line, path lists enclosed in double quotes so spaces in paths survive. Both
 * {@code maven-compiler-plugin} (via {@code <compilerArgument>@...</compilerArgument>}) and the
 * {@code java} launcher (via {@code java @...}) consume the same format, which means the same
 * argfile can drive both {@code maven-compiler-plugin}'s compile goal and
 * {@code maven-surefire-plugin}'s {@code <argLine>}.
 */
abstract class AbstractClassifyPathMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * Explicit list of module names to seed the classifier's required-module preference.
     * Takes priority over automatic seed derivation. Useful when the automatic strategies
     * don't produce the set of names you need — for example, when a test module needs to
     * preserve a module that isn't transitively reachable from its direct dependencies.
     */
    @Parameter
    private List<String> seedModules;

    protected abstract List<Path> getCandidates() throws DependencyResolutionRequiredException;

    protected abstract Optional<Path> getSourceModuleInfo();

    protected abstract Path getArgfile();

    /**
     * Prefix for the project properties this Mojo exposes. Subclasses return something like
     * {@code "spin.classify.compile"} or {@code "spin.classify.test"}; the Mojo then sets
     * {@code <prefix>.modulepath} and {@code <prefix>.classpath} on the project so the pom
     * can reference them from {@code <compilerArgs>} without going through an {@code @argfile}
     * indirection — maven-compiler-plugin writes its own outer argfile and javac does not
     * recursively expand nested {@code @argfile} references, so {@code @argfile} inside
     * {@code <testCompilerArgument>} fails with "invalid flag".
     */
    protected abstract String getPropertyPrefix();

    @Override
    public void execute() throws MojoExecutionException {
        try {
            final List<Path> candidates = getCandidates();
            final Set<String> seed = computeSeed(candidates);

            getLog().info("classifying " + candidates.size()
                + " candidates with seed " + seed);

            final ModuleGraphClassifier.Classification classification =
                ModuleGraphClassifier.classify(
                    candidates,
                    seed,
                    msg -> getLog().info("[classify] " + msg));

            // Filter the seed to only module names that are actually present on the
            // curated module-path. The seed may contain names from `requires static`
            // (optional deps) or from modules whose jars aren't in the closure at all
            // (e.g. com.github.spotbugs.annotations, org.wildfly.common). Emitting
            // those in --add-modules would cause FindException at JVM boot.
            final Set<String> resolvedModuleNames = new LinkedHashSet<>();
            for (final Path p : classification.modulePath()) {
                final java.lang.module.ModuleDescriptor d =
                    ModuleGraphClassifier.readDescriptor(p);
                if (d != null && seed.contains(d.name())) {
                    resolvedModuleNames.add(d.name());
                }
            }

            writeArgfile(getArgfile(), classification, resolvedModuleNames);

            final String prefix = getPropertyPrefix();
            project.getProperties().setProperty(
                prefix + ".modulepath", join(classification.modulePath()));
            project.getProperties().setProperty(
                prefix + ".classpath", join(classification.classPath()));

            getLog().info("wrote " + getArgfile()
                + " (module-path=" + classification.modulePath().size()
                + ", class-path=" + classification.classPath().size() + ")");
            getLog().info("exposed project properties " + prefix + ".modulepath, "
                + prefix + ".classpath");
        } catch (final Exception e) {
            throw new MojoExecutionException(
                "Failed to classify path for project " + project.getArtifactId()
                    + ": " + e.getMessage(), e);
        }
    }

    private Set<String> computeSeed(final List<Path> candidates) {
        // 1. Explicit override from the pom
        if (seedModules != null && !seedModules.isEmpty()) {
            return ModuleGraphClassifier.closeOverRequires(
                candidates, new LinkedHashSet<>(seedModules));
        }

        // 2. Source module-info.java, if present
        final Optional<Path> moduleInfo = getSourceModuleInfo();
        if (moduleInfo.isPresent()) {
            final Set<String> fromSource =
                ModuleGraphClassifier.collectRequiredModuleNames(moduleInfo, candidates);
            if (!fromSource.isEmpty()) {
                return fromSource;
            }
        }

        // 3. Direct Maven dependencies' module names, transitively closed
        final Set<String> directDepNames = new LinkedHashSet<>();
        for (final Artifact artifact : project.getArtifacts()) {
            // dependencyTrail has [project, this] for direct deps and adds a link per hop
            // for transitive deps. Length 2 means "declared directly in this pom".
            final List<String> trail = artifact.getDependencyTrail();
            if (trail == null || trail.size() != 2) {
                continue;
            }
            final File file = artifact.getFile();
            if (file == null || !file.exists()) {
                continue;
            }
            final ModuleDescriptor d =
                ModuleGraphClassifier.readDescriptor(file.toPath());
            if (d != null) {
                directDepNames.add(d.name());
            }
        }
        if (!directDepNames.isEmpty()) {
            return ModuleGraphClassifier.closeOverRequires(candidates, directDepNames);
        }

        // 4. Empty fallback — tier (a) will not fire
        return Set.of();
    }

    private static void writeArgfile(final Path argfile,
                                     final ModuleGraphClassifier.Classification classification,
                                     final Set<String> seedModuleNames)
        throws IOException {
        final Path parent = argfile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter w = Files.newBufferedWriter(argfile)) {
            if (!classification.modulePath().isEmpty()) {
                w.write("--module-path\n");
                w.write("\"" + join(classification.modulePath()) + "\"\n");
                // Root-resolve the specific modules the project needs so their `provides`
                // declarations are visible to ServiceLoader. We emit the seed set (the
                // modules transitively reachable via `requires` from the project's deps)
                // rather than ALL-MODULE-PATH, because ALL-MODULE-PATH forces resolution
                // of EVERY observable module — including ones with broken requires chains
                // (e.g. org.jboss.threads requiring org.wildfly.common, but wildfly-common
                // is a filename-derived automatic module named wildfly.common, not
                // org.wildfly.common). Limiting to the seed avoids triggering those failures.
                //
                // Uses `=` syntax (not space-separated) so spawn.build's
                // AddModules.inherited() can parse it from
                // ManagementFactory.getRuntimeMXBean().getInputArguments() when a child
                // process inherits the parent JVM's module configuration.
                if (!seedModuleNames.isEmpty()) {
                    w.write("--add-modules=" + String.join(",", seedModuleNames) + "\n");
                }
            }
            if (!classification.classPath().isEmpty()) {
                w.write("--class-path\n");
                w.write("\"" + join(classification.classPath()) + "\"\n");
            }
        }
    }

    private static String join(final List<Path> paths) {
        return paths.stream()
            .map(Path::toString)
            .collect(Collectors.joining(File.pathSeparator));
    }
}
