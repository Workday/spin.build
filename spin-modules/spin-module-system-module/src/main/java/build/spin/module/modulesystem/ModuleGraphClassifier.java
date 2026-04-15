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

import build.base.version.Version;
import build.base.version.VersionOrder;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolutionException;
import java.lang.module.ResolvedModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified JPMS module-graph classifier. Given a flat list of candidate jars (or exploded
 * module directories), decides which belong on {@code --module-path} and which on
 * {@code -classpath}. This is the single home for every place in spin that used to
 * hand-roll split-package detection, version dedupe, and module-path classification —
 * previously {@code Launcher.findSplitPackageConflicts},
 * {@code AbstractCompile.resolveConflicts}, and {@code AbstractJavaLinker.classifyForModulePath}.
 *
 * <h2>Algorithm</h2>
 *
 * <p>One pass over the candidate set, using {@link ModuleFinder} to get each candidate's
 * {@link ModuleDescriptor} (proper modules, automatic modules, and filename-derived automatic
 * modules are all handled uniformly by the finder):
 *
 * <ol>
 *   <li><b>Package inversion.</b> Build a map of {@code package → owners}; any package owned
 *       by more than one candidate is a conflict.</li>
 *   <li><b>Version dedupe.</b> Conflicting jars with the same base name (version stripped)
 *       are grouped; newest wins via semantic version ordering ({@link Version}),
 *       older versions are <em>superseded</em> and routed to classpath (or discarded at
 *       link time).</li>
 *   <li><b>Subset dedupe.</b> If jar A's packages are a strict subset of jar B's packages,
 *       A is superseded (subsumed by B). Handles patterns like {@code failureaccess} ⊂
 *       {@code guava}.</li>
 *   <li><b>Split-package resolution</b> — for every remaining conflict (packages still
 *       owned by 2+ candidates after the dedupe passes), pick owners to <em>demote</em>
 *       under a three-tier policy:
 *       <ol type="a">
 *         <li><b>Required-module preference.</b> If exactly one owner's module name is in
 *             the transitive {@code requires} closure of the seed set, that owner is kept
 *             and the others are demoted. Without this tier, the old "demote both" policy
 *             would drop {@code maven.settings} when it shares {@code
 *             org.apache.maven.settings.v4} with the helper jar {@code maven.support} — and
 *             then any {@code requires maven.settings} in the root fails to resolve.</li>
 *         <li><b>Proper-over-automatic.</b> If any owner is a proper JPMS module (has a
 *             real {@code module-info.class}) and the others are only automatic modules,
 *             the proper ones win and the automatic ones are demoted. Handles cases like
 *             {@code wildfly-common} (proper) vs {@code jboss-logmanager} (automatic)
 *             sharing {@code org.wildfly.common.*}.</li>
 *         <li><b>Demote all.</b> Fall back when neither tier applies. Automatic modules on
 *             {@code --module-path} can still reach demoted classes through {@code
 *             ALL-UNNAMED}, so this is safe at runtime — it just means that the JPMS
 *             package-uniqueness rule is satisfied by moving the conflicting jars off the
 *             module path entirely.</li>
 *       </ol>
 *   </li>
 * </ol>
 *
 * <p>Candidates that don't own any package at all (unreadable jars, empty directories) fall
 * through unchanged — they're routed to the module-path bucket where filename-based automatic
 * module discovery can still pick them up. Two proper JPMS modules sharing a real package
 * can't be silently resolved — tier (b) would keep both and tier (c) fires, demoting neither,
 * leaving JPMS to report a genuine split-package error. That's intentional: two proper
 * modules with a real conflict represent a broken dependency graph.
 *
 * <h2>Entry points</h2>
 *
 * <ul>
 *   <li>{@link #classify} — package-level conflict resolution only. Use at
 *       <em>compile time</em> (javac, javadoc, jdeps) where the downstream tool runs its
 *       own JPMS resolution. Everything the classifier doesn't demote/supersede stays on
 *       the module path.</li>
 *   <li>{@link #classifyAndResolve} — runs {@code classify} <em>and then</em>
 *       {@link Configuration#resolve}, keeping only the jars that actually participate in
 *       the resolved module graph reachable from {@code rootModuleName}. Use at
 *       <em>launch time</em> (spin1's exec-maven-plugin bridge, jlink image launch) where
 *       the result must be a precise module graph, not merely "no split packages".</li>
 * </ul>
 */
public final class ModuleGraphClassifier {

    private ModuleGraphClassifier() {}

    /** Output of {@link #classify} / {@link #classifyAndResolve}. */
    public record Classification(List<Path> modulePath, List<Path> classPath) {}

    /**
     * Intermediate output of {@link #resolveConflicts}: which candidates were <em>superseded</em>
     * by a version-duplicate or subset match, and which were <em>demoted</em> off the module
     * path by a split-package decision. These two sets are disjoint: a superseded jar is not
     * also reported as demoted. At launch time both should be kept out of the module-path set;
     * at compile time superseded jars are typically dropped entirely (older duplicates) while
     * demoted jars go to classpath.
     */
    public record ConflictResolution(Set<Path> superseded, Set<Path> demoted) {}

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Classify candidates into module-path vs classpath based on split-package / version /
     * subset conflict resolution alone — no {@link Configuration#resolve} is performed.
     *
     * @param candidates          all candidate jars or exploded module directories
     * @param seedRequiredNames   module names that should be preferred when resolving
     *                            split-package conflicts. Typically the root module plus any
     *                            module it directly requires; the classifier computes the
     *                            transitive closure via {@link #closeOverRequires}. Pass
     *                            {@link Set#of()} to disable the required-module preference
     *                            (tier (a) is skipped; tiers (b) and (c) still apply).
     * @param log                 per-decision log sink; the classifier calls this once per
     *                            supersede / demote action with a pre-formatted human-readable
     *                            message
     * @return a {@link Classification} whose {@code modulePath} contains all candidates that
     *         survived conflict resolution (in original order) and whose {@code classPath}
     *         contains everything that was superseded or demoted (also in original order)
     */
    public static Classification classify(final List<Path> candidates,
                                          final Set<String> seedRequiredNames,
                                          final Consumer<String> log) {
        final ConflictResolution resolution = resolveConflicts(candidates, seedRequiredNames, log);
        final List<Path> modulePath = new ArrayList<>();
        final List<Path> classPath = new ArrayList<>();
        for (final Path jar : candidates) {
            if (resolution.superseded().contains(jar) || resolution.demoted().contains(jar)) {
                classPath.add(jar);
            }
            else {
                modulePath.add(jar);
            }
        }
        return new Classification(modulePath, classPath);
    }

    /**
     * Classify candidates into module-path vs classpath, then run {@link Configuration#resolve}
     * to prune the module-path to only the jars reachable from {@code rootModuleName} via the
     * JPMS module graph. Anything not in the resolved graph moves to classpath.
     *
     * @param candidates          all candidate jars or exploded module directories
     * @param seedRequiredNames   module names preferred when resolving split-package
     *                            conflicts; usually {@code Set.of(rootModuleName)} but callers
     *                            that compute their own seed set (e.g. direct requires parsed
     *                            from a source module-info) may pass a richer set
     * @param rootModuleName      the root to start the JPMS resolution from; must be findable
     *                            in the candidate finder after conflict resolution
     * @param parentConfiguration parent configuration for {@link Configuration#resolve}; use
     *                            {@link ModuleLayer#boot() ModuleLayer.boot()}{@code
     *                            .configuration()} when the caller's boot layer has no
     *                            collision with candidate module names, or
     *                            {@link Configuration#empty()} when it does (see the
     *                            jlink-linker case — spin's own boot layer already has
     *                            {@code build.spin.module.*} modules, so re-asking the boot
     *                            layer to resolve them from the finder produces "reads more
     *                            than one module named X" errors)
     * @param beforeFinder        the "before" finder for {@link Configuration#resolve};
     *                            typically {@link ModuleFinder#ofSystem()} when
     *                            {@code parentConfiguration} is
     *                            {@link Configuration#empty()}, or an empty
     *                            {@code ModuleFinder.of()} otherwise
     * @param log                 per-decision log sink (as for {@link #classify})
     * @return a {@link Classification} whose {@code modulePath} is the resolved graph
     *         filtered to {@code file:}-scheme locations that exist in the input candidate
     *         set, and whose {@code classPath} is every remaining input not in the resolved
     *         graph
     * @throws IllegalStateException if the JPMS resolution fails (wrapped
     *                               {@link FindException} or {@link ResolutionException})
     */
    public static Classification classifyAndResolve(final List<Path> candidates,
                                                    final Set<String> seedRequiredNames,
                                                    final String rootModuleName,
                                                    final Configuration parentConfiguration,
                                                    final ModuleFinder beforeFinder,
                                                    final Consumer<String> log) {
        final ConflictResolution resolution = resolveConflicts(candidates, seedRequiredNames, log);
        final Set<Path> moduleCandidates = new LinkedHashSet<>(candidates);
        moduleCandidates.removeAll(resolution.superseded());
        moduleCandidates.removeAll(resolution.demoted());

        final ModuleFinder finder = ModuleFinder.of(moduleCandidates.toArray(new Path[0]));
        final Configuration config;
        try {
            config = parentConfiguration.resolve(beforeFinder, finder, Set.of(rootModuleName));
        }
        catch (final FindException | ResolutionException e) {
            throw new IllegalStateException("Unable to resolve JPMS module graph from root ["
                + rootModuleName + "]: " + e.getMessage(), e);
        }

        // Restrict to paths the caller actually handed us. Resolved system modules
        // (java.base, etc.) have jrt: locations we must not try to treat as files; the
        // filter on candidateSet naturally excludes them.
        final Set<Path> candidateSet = new LinkedHashSet<>(candidates);
        final Set<Path> resolved = new LinkedHashSet<>();
        for (final ResolvedModule rm : config.modules()) {
            rm.reference().location()
                .filter(uri -> "file".equals(uri.getScheme()))
                .map(Path::of)
                .filter(candidateSet::contains)
                .ifPresent(resolved::add);
        }

        final List<Path> modulePath = new ArrayList<>();
        final List<Path> classPath = new ArrayList<>();
        for (final Path jar : candidates) {
            if (resolved.contains(jar)) {
                modulePath.add(jar);
            }
            else {
                classPath.add(jar);
            }
        }
        return new Classification(modulePath, classPath);
    }

    /**
     * Compute the transitive closure of module names reachable from {@code seedNames} via
     * {@code requires} directives through the candidate finder. Names that don't resolve in
     * the candidate finder (system modules, unresolved placeholders) are still recorded so
     * a root's {@code requires maven.settings} is respected even when {@code maven.settings}
     * sits alongside a split-package sibling in the candidate set — what matters for the
     * split-package decision is whether the <em>name</em> is transitively required, not
     * whether we can walk through its descriptor.
     */
    public static Set<String> closeOverRequires(final List<Path> candidates,
                                                final Set<String> seedNames) {
        if (seedNames.isEmpty()) {
            return Set.of();
        }
        final ModuleFinder finder = ModuleFinder.of(candidates.toArray(new Path[0]));
        final Set<String> visited = new LinkedHashSet<>();
        final Deque<String> frontier = new ArrayDeque<>(seedNames);
        while (!frontier.isEmpty()) {
            final String name = frontier.removeFirst();
            if (!visited.add(name)) {
                continue;
            }
            finder.find(name).ifPresent(ref -> {
                for (final ModuleDescriptor.Requires req : ref.descriptor().requires()) {
                    if (!visited.contains(req.name())) {
                        frontier.add(req.name());
                    }
                }
            });
        }
        return visited;
    }

    /**
     * Convenience for compile-time callers (javac, javadoc) that have a source
     * {@code module-info.java} file for the root module being processed. Parses the direct
     * {@code requires} names from the source textually, then delegates the transitive walk
     * to {@link #closeOverRequires}. The result is the right seed set to feed into
     * {@link #classify} so a {@code requires foo} in the source module prevents {@code foo}
     * from being demoted together with its split-package sibling.
     *
     * @param sourceModuleInfoJava the source {@code module-info.java}, or empty for
     *                             non-modular compilation (returns an empty set)
     * @param candidates           compile-time classpath jars / directories to walk
     * @return the set of module names the root transitively requires, or an empty set if
     *         the source file is missing, unreadable, or declares no {@code requires}
     */
    public static Set<String> collectRequiredModuleNames(
            final Optional<Path> sourceModuleInfoJava,
            final List<Path> candidates) {
        if (sourceModuleInfoJava.isEmpty()) {
            return Set.of();
        }
        final Set<String> direct = parseSourceRequires(sourceModuleInfoJava.get());
        if (direct.isEmpty()) {
            return Set.of();
        }
        return closeOverRequires(candidates, direct);
    }

    private static final Pattern REQUIRES_PATTERN = Pattern.compile(
        "\\brequires\\s+(?:(?:static|transitive)\\s+){0,2}([\\w.]+)\\s*;");

    /**
     * Parse the {@code requires} directives from a source {@code module-info.java} file.
     * Block and line comments are stripped first so commented-out requires don't leak into
     * the result. {@code static} and {@code transitive} modifiers are tolerated in any
     * order and combination.
     *
     * <p>Exposed as public because both {@code AbstractCompile} and {@code AbstractJavaDoc}
     * in {@code spin-java-module} need to seed the classifier from a source module-info at
     * javac / javadoc invocation time.
     */
    public static Set<String> parseSourceRequires(final Path moduleInfoJava) {
        final String source;
        try {
            source = Files.readString(moduleInfoJava);
        }
        catch (final IOException e) {
            return Set.of();
        }
        // strip block comments, then line comments
        String stripped = source.replaceAll("(?s)/\\*.*?\\*/", "");
        stripped = stripped.replaceAll("//[^\n]*", "");
        final Set<String> names = new LinkedHashSet<>();
        final Matcher m = REQUIRES_PATTERN.matcher(stripped);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /**
     * Compute the conflict resolution: which candidates should be treated as superseded
     * (version duplicate or strict subset) and which should be demoted off the module-path
     * (split-package loser). See the class javadoc for the full algorithm.
     *
     * <p>Package ownership is derived from {@link ModuleDescriptor#packages()} via
     * {@link ModuleFinder}, which covers proper modules (packages come from
     * {@code module-info.class}), automatic modules with {@code Automatic-Module-Name}
     * (scanned from {@code .class} entries), and filename-derived automatic modules
     * (same scan). Candidates that the finder can't interpret as modules at all — e.g.
     * unreadable jars, directories with no classes — contribute no packages and so are
     * silently excluded from conflict analysis.
     */
    public static ConflictResolution resolveConflicts(final List<Path> candidates,
                                                      final Set<String> seedRequiredNames,
                                                      final Consumer<String> log) {
        // For each candidate, look up its descriptor through the finder.
        // Per-path (rather than composite) so two candidates with the same derived module
        // name (a pathological but possible state) don't silently collide in a single
        // finder — we still record both and let them conflict normally.
        final Map<Path, ModuleDescriptor> descriptorsByPath = new LinkedHashMap<>();
        final Map<Path, Set<String>> packagesByPath = new LinkedHashMap<>();
        for (final Path path : candidates) {
            final ModuleDescriptor descriptor = readDescriptor(path);
            if (descriptor == null) {
                continue;
            }
            descriptorsByPath.put(path, descriptor);
            if (!descriptor.packages().isEmpty()) {
                packagesByPath.put(path, descriptor.packages());
            }
        }

        // invert: package → owners
        final Map<String, List<Path>> packageOwners = new HashMap<>();
        packagesByPath.forEach((path, packages) -> {
            for (final String pkg : packages) {
                packageOwners.computeIfAbsent(pkg, k -> new ArrayList<>()).add(path);
            }
        });

        // collect contested packages
        final Map<String, List<Path>> conflicts = new HashMap<>();
        packageOwners.forEach((pkg, owners) -> {
            if (owners.size() > 1) {
                conflicts.put(pkg, owners);
            }
        });

        final Set<Path> superseded = new HashSet<>();
        final Set<Path> demoted = new HashSet<>();

        if (conflicts.isEmpty()) {
            return new ConflictResolution(superseded, demoted);
        }

        // transitive requires closure for tier (a) of split-package resolution
        final Set<String> requiredNames = closeOverRequires(candidates, seedRequiredNames);

        // -- Version dedupe: group all conflicting jars by base name, keep newest
        final Set<Path> allConflicting = new HashSet<>();
        conflicts.values().forEach(allConflicting::addAll);

        final Map<String, List<Path>> byBaseName = new HashMap<>();
        for (final Path path : allConflicting) {
            byBaseName.computeIfAbsent(deriveBaseName(path), k -> new ArrayList<>()).add(path);
        }
        for (final Map.Entry<String, List<Path>> entry : byBaseName.entrySet()) {
            final List<Path> group = entry.getValue();
            if (group.size() > 1) {
                group.sort(jarVersionDescending());
                final Path kept = group.get(0);
                for (int i = 1; i < group.size(); i++) {
                    log.accept(String.format(
                        "Version duplicate [%s] superseded by [%s] — dropping",
                        group.get(i).getFileName(), kept.getFileName()));
                    superseded.add(group.get(i));
                }
            }
        }

        // -- Subset dedupe: if A's packages ⊂ B's, supersede A
        final Set<Path> remaining = new LinkedHashSet<>(allConflicting);
        remaining.removeAll(superseded);
        for (final Path a : new ArrayList<>(remaining)) {
            if (superseded.contains(a)) {
                continue;
            }
            final Set<String> pkgsA = packagesByPath.getOrDefault(a, Set.of());
            for (final Path b : remaining) {
                if (a.equals(b) || superseded.contains(b)) {
                    continue;
                }
                final Set<String> pkgsB = packagesByPath.getOrDefault(b, Set.of());
                if (pkgsB.containsAll(pkgsA) && pkgsB.size() > pkgsA.size()) {
                    log.accept(String.format(
                        "Subset jar [%s] (%d pkgs) subsumed by [%s] (%d pkgs) — dropping",
                        a.getFileName(), pkgsA.size(), b.getFileName(), pkgsB.size()));
                    superseded.add(a);
                    break;
                }
            }
        }

        // -- Split-package resolution: tiered demotion policy
        for (final Map.Entry<String, List<Path>> entry : conflicts.entrySet()) {
            final List<Path> owners = entry.getValue().stream()
                .filter(p -> !superseded.contains(p))
                .toList();
            if (owners.size() <= 1) {
                continue;
            }

            log.accept(String.format("True split package [%s] across %s — resolving",
                entry.getKey(),
                owners.stream().map(p -> p.getFileName().toString()).toList()));

            // tier (a): required-module preference
            if (!requiredNames.isEmpty()) {
                final List<Path> requiredOwners = new ArrayList<>();
                final List<Path> nonRequiredOwners = new ArrayList<>();
                for (final Path owner : owners) {
                    final ModuleDescriptor d = descriptorsByPath.get(owner);
                    final String name = d == null ? "" : d.name();
                    if (!name.isEmpty() && requiredNames.contains(name)) {
                        requiredOwners.add(owner);
                    }
                    else {
                        nonRequiredOwners.add(owner);
                    }
                }
                if (requiredOwners.size() == 1 && !nonRequiredOwners.isEmpty()) {
                    log.accept(String.format("  keeping required module [%s], demoting %s",
                        requiredOwners.get(0).getFileName(),
                        nonRequiredOwners.stream().map(p -> p.getFileName().toString()).toList()));
                    demoted.addAll(nonRequiredOwners);
                    continue;
                }
            }

            // tier (b): proper modules win over automatic
            final boolean hasProperModule = owners.stream().anyMatch(p -> {
                final ModuleDescriptor d = descriptorsByPath.get(p);
                return d != null && !d.isAutomatic();
            });
            for (final Path owner : owners) {
                final ModuleDescriptor d = descriptorsByPath.get(owner);
                final boolean isProper = d != null && !d.isAutomatic();
                if (!hasProperModule || !isProper) {
                    demoted.add(owner);
                }
            }
            // tier (c) is implicit in tier (b) when `hasProperModule == false` —
            // every owner is demoted.
        }

        return new ConflictResolution(superseded, demoted);
    }

    // ---------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------

    /**
     * Returns the {@link ModuleDescriptor} that {@link ModuleFinder} would assign to
     * {@code path}, or {@code null} if the path isn't recognised as a module (not a jar,
     * unreadable, directory with no {@code module-info.class} and no scannable classes,
     * etc.).
     */
    public static ModuleDescriptor readDescriptor(final Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            return ModuleFinder.of(path).findAll().stream()
                .findFirst()
                .map(ModuleReference::descriptor)
                .orElse(null);
        }
        catch (final FindException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns {@code true} if {@code path} is a named JPMS module from {@link ModuleFinder}'s
     * point of view — a proper module, an automatic module with {@code Automatic-Module-Name},
     * or a filename-derived automatic module. Unnamed jars return {@code false}.
     */
    public static boolean isNamedModule(final Path path) {
        return readDescriptor(path) != null;
    }

    /**
     * Derives the base name of a jar by stripping its version suffix and {@code .jar}
     * extension. Used to detect when one version of a library supersedes another
     * (e.g. {@code jboss-logging-3.3.1.Final.jar} → {@code jboss-logging}).
     */
    public static String deriveBaseName(final Path jar) {
        if (jar == null) {
            return "";
        }
        String name = jar.getFileName().toString();
        if (name.endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.replaceAll("-(\\d.*)$", "");
    }

    /**
     * Returns a {@link Comparator} that orders jars by version descending using semantic
     * version ordering via {@link Version}, so that {@code 11.0.0} correctly wins
     * over {@code 2.0.0} — lexicographic ordering would prefer {@code 2} because {@code
     * '2' > '1'}. Jars without a parseable version fall back to lexicographic ordering.
     */
    public static Comparator<Path> jarVersionDescending() {
        return (a, b) -> {
            final String va = extractVersion(a);
            final String vb = extractVersion(b);
            if (va.isEmpty() && vb.isEmpty()) {
                return 0;
            }
            if (va.isEmpty()) {
                return 1;
            }
            if (vb.isEmpty()) {
                return -1;
            }
            try {
                return VersionOrder.MAVEN.compare(Version.parse(vb), Version.parse(va));
            }
            catch (final IllegalArgumentException ignored) {
                return vb.compareTo(va);
            }
        };
    }

    private static String extractVersion(final Path jar) {
        final String base = deriveBaseName(jar);
        final String name = jar.getFileName().toString().replace(".jar", "");
        return name.length() > base.length() ? name.substring(base.length() + 1) : "";
    }
}
