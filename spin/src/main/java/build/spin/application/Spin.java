package build.spin.application;

/*-
 * #%L
 * Spin Command Line Application
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
import build.base.commandline.CommandLineParser;
import build.base.commandline.CommandLineParser.HelpException;
import build.base.configuration.ConfigurationBuilder;
import build.base.foundation.Introspection;
import build.base.option.JDKVersion;
import build.base.option.WorkingDirectory;
import build.base.table.Table;
import build.spin.AssetCache;
import build.spin.BackgroundProcessor;
import build.spin.Daemon;
import build.spin.Engine;
import build.spin.Invocable;
import build.spin.Program;
import build.spin.ProgramExecutionException;
import build.spin.Project;
import build.spin.Task;
import build.spin.Workspace;
import build.spin.common.DefaultAssetCache;
import build.spin.engine.DefaultEngine;
import build.spin.option.EngineVersion;
import build.spin.option.NetworkAccess;
import build.spin.option.OperatingSystem;
import build.spin.option.ServerMode;
import build.spin.option.ServerPort;
import build.spin.option.Verbose;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Spin {

    /**
     * Ensure {@link Spin} can't be instantiated.
     */
    private Spin() {}

    /**
     * The {@code spin} application entry point.
     *
     * @param args the command line arguments
     */
    @SuppressWarnings("unchecked")
    public static void main(final String[] args) {

        // ---------------------------------------------
        // PRE-FLIGHT: short-circuit flags that need no workspace
        for (final String arg : args) {
            if ("--version".equals(arg)) {
                System.out.println(EngineVersion.autodetect().get());
                System.exit(0);
            }
        }

        // ---------------------------------------------
        // PHASE 0: Output Banner and initial diagnostics
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Spin.class.getResourceAsStream("/banner.txt")))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.err.println(line);
            }
        } catch (final Exception e) {
            System.err.println("[spin] Failed to read banner.txt");
            e.printStackTrace(System.err);
        }

        // ---------------------------------------------
        // PHASE 1: Bootstrap initial OptionsByTypes to configure Spin (based on the
        // CommandLine)

        // establish a CommandLineParser that we can use to parse command-line options
        final CommandLineParser parser = new CommandLineParser();

        // ignore any command-line arguments we don't yet understand
        // (we assume they are either Arguments or provided by Extensions - which we don't yet know about)
        parser.setUnknownOptionConsumer(CommandLineParser.IGNORE_UNKNOWN_OPTIONS);

        // include spin-specific bootstrap Options
        try {
            parser.add("-w", WorkingDirectory.class, "of", String.class);
            parser.add(NetworkAccess.class);
            parser.add(Verbose.class);
            parser.add(ServerMode.class);
            parser.add(ServerPort.class);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("Failed to configure BootStrap Options", e);
        }

        // attempt to establish the bootstrap Configuration from the command-line
        final ConfigurationBuilder bootstrapOptionsByType;
        try {
            bootstrapOptionsByType = parser.parse(args);
        } catch (final HelpException e) {
            System.out.println(e.getMessage());
            System.exit(0);
            return;
        }

        // ensure there's a default JDKVersion to use
        bootstrapOptionsByType.compute(JDKVersion.class, existing -> existing != null ? existing : JDKVersion.current());

        // ensure there's an OperatingSystem to use
        bootstrapOptionsByType.compute(OperatingSystem.class, existing -> existing != null ? existing : OperatingSystem.detect());

        // we can now obtain the EngineVersion
        bootstrapOptionsByType.compute(EngineVersion.class, existing -> existing != null ? existing : EngineVersion.autodetect());
        System.err.printf("[spin] Engine Version: %s\n", bootstrapOptionsByType.get(EngineVersion.class));
        System.err.printf("[spin] Operating System: %s\n", bootstrapOptionsByType.get(OperatingSystem.class));
        System.err.printf("[spin] Java Version: %s\n", JDKVersion.current());
        System.err.printf("[spin] Server Port: %s\n", bootstrapOptionsByType.get(ServerPort.class));
        System.err.println();

        // ---------------------------------------------
        // PHASE 2: Establish the Engine

        // establish the FileSystem for the Engine
        final FileSystem fileSystem = FileSystems.getDefault();

        // ensure there's a WorkingDirectory Option
        final Path userPath = fileSystem.getPath(System.getProperty("user.dir"));
        System.err.printf("[spin] Current Folder: %s\n", userPath);

        final Optional<WorkingDirectory> workingDirectory = Optional.ofNullable(
                bootstrapOptionsByType.getWithoutDefault(WorkingDirectory.class))
                .map(directory -> userPath.resolve(fileSystem.getPath(directory.get())))
                .map(path -> WorkingDirectory.of(path.normalize().toString()));

        workingDirectory.ifPresent(bootstrapOptionsByType::add);

        // output the Bootstrap Options
        final Table table = Table.create();
        bootstrapOptionsByType.stream()
                .forEach(option -> table.addRow(option.getClass().getSimpleName(), option.toString()));

        System.err.printf("[spin] Bootstrap Options:\n%s\n", table);

        // create the Engine
        final Engine engine = new DefaultEngine(
                Thread.currentThread().getContextClassLoader(),
                fileSystem,
                bootstrapOptionsByType.build(),
                Optional.of(parser),
                Optional.ofNullable(args),
                Optional.of(observable -> observable.subscribe(event -> System.err.printf("%s\n", event))));

        // ---------------------------------------------
        // PHASE 3: Discover the Workspace and the Project

        final Path path = fileSystem.getPath("")
                .toAbsolutePath()
                .resolve(bootstrapOptionsByType.get(WorkingDirectory.class).get());

        System.err.printf("[spin] Discovering Workspace for Project in [%s]\n", path);

        final Workspace workspace = engine.createWorkspace(path)
                .orElseThrow(() -> new RuntimeException("Failed to discover workspace containing " + path));

        final Project project = workspace.getProject(path)
                .orElseThrow(() -> new IllegalStateException("No project found in workspace [" + workspace.path() + "] at path [" + path + "]"));

        System.err.printf("[spin] Detected Project [%s] at [%s]\n", project.name(), project.path());
        System.err.printf("[spin] (Within Workspace [%s] at [%s])\n", project.name(), project.path());

        // output the project structure as a tree
        final StringBuilder builder = new StringBuilder(4096);
        workspace.treeify(builder,
                "",
                "",
                p -> p.name() + (p == project ? " *" : ""));

        System.err.printf("[spin] Workspace Structure:\n%s\n", builder);

        final var availableTasks = workspace.stream()
                .flatMap(Project::invocables)
                .map(Invocable::getTaskName)
                .distinct()
                .sorted()
                .toList();

        System.err.printf("[spin] Available Tasks: %s\n", availableTasks);

        // ---------------------------------------------
        // start in "server mode" or execute one or more programs

        if (engine.options().get(ServerMode.class) == ServerMode.ENABLED) {

            System.err.printf("[spin] Starting in Server Mode...\n");

            // collect the BackgroundProcessors to notify (start with the Servers, then add the Daemons)
            final LinkedHashSet<BackgroundProcessor> processors = new LinkedHashSet<>();
            engine.services(BackgroundProcessor.class)
                    .forEach(processors::add);

            workspace.stream()
                    .forEach(prj -> prj.resources()
                            .filter(Daemon.class::isInstance)
                            .map(Daemon.class::cast)
                            .forEach(processors::add));

            // initialize the BackgroundProcessors
            processors.forEach(BackgroundProcessor::onInitialize);

            // start the BackgroundProcessors
            // (track them by the CompletableFuture returned from .start())
            final LinkedHashMap<CompletableFuture<Integer>, BackgroundProcessor> futures = new LinkedHashMap<>();

            // the composed CompletableFuture that caused termination
            final CompletableFuture<Integer> terminationFuture = new CompletableFuture<>();

            // a Consumer to start a BackgroundProcessor
            final Consumer<? super BackgroundProcessor> start = processor -> {

                final var processorName = Introspection.describe(processor.getClass());
                System.err.printf("[spin] Starting: [%s]\n", processorName);

                final var exceptional = processor.start();

                if (exceptional.isEmpty()) {
                    // didn't start the processor... that's ok!
                } else if (exceptional.isException()) {
                    // failed to start the processor
                    System.err.printf("[spin] Starting Failed (exceptionally): [%s]\n", processorName);
                    exceptional.exception().get().printStackTrace(System.err);
                } else {
                    final var future = exceptional.orElseThrow(() -> new IllegalStateException("Unexpected empty Exceptional for processor: " + processorName));

                    futures.put(future, processor);

                    if (!future.isDone()) {
                        System.err.printf("[spin] Started: [%s]\n", processorName);
                    }

                    // terminate when the BackgroundProcessor terminates
                    future.whenComplete((statusCode, throwable) -> {
                        if (throwable == null) {
                            System.err.printf("[spin] Terminated: [%s] (%d)\n", processorName, statusCode);
                            terminationFuture.complete(statusCode);
                        } else {
                            System.err.printf("[spin] Terminated (exceptionally): [%s]\n", processorName);
                            throwable.printStackTrace(System.err);
                            terminationFuture.completeExceptionally(throwable);
                        }
                    });
                }
            };

            // start the BackgroundProcessors
            processors.forEach(start);

            if (futures.isEmpty()) {
                System.err.printf("[spin] No Background Processors (Servers or Daemons) Discovered or Started.\n");

                System.exit(-1);
            }

            // cancel the BackgroundProcess CompletableFutures when termination occurs
            terminationFuture.whenComplete((statusCode, throwable) -> futures.keySet()
                    .forEach(future -> future.cancel(true)));

            // ensure the termination future terminated!
            terminationFuture.join();

            try {
                // close the Workspace AutoClosables
                workspace.close();

                engine.close();

                // attempt to exit with the termination status code
                System.exit(terminationFuture.get());
            } catch (final Exception e) {
                System.err.printf("[spin] Unexpected Spin Termination Failure\n");
                e.printStackTrace(System.err);

                System.exit(-1);
            }
        } else {
            // ---------------------------------------------
            // PHASE 4: Create and Execute the Programs for the Tasks

            // establish the Configuration for the Program (based on the Bootstrap Options)
            final ConfigurationBuilder programOptions = ConfigurationBuilder.create().include(bootstrapOptionsByType);

            // capture the unknown CommandLineOptions as arguments (they capture the desired program to execute)
            parser.setUnknownOptionConsumer(CommandLineParser.CAPTURE_UNKNOWN_OPTIONS_AS_ARGUMENTS);

            final ConfigurationBuilder commandLineOptions = parser.parse(args);
            programOptions.include(commandLineOptions);

            // determine the tasks to execute based on the Program CommandLine.Arguments
            final LinkedHashSet<String> tasks = programOptions.stream(CommandLine.Argument.class)
                    .map(CommandLine.Argument::get)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (tasks.isEmpty()) {
                System.err.printf("[spin] No tasks specified.\n");
                workspace.close();
                System.exit(0);
            }

            // establish a Task result ExecutionCache, allowing cacheable Task results to be
            // shared between Programs
            final AssetCache shared = DefaultAssetCache.create();

            tasks.forEach(task -> {
                final Task.Pattern pattern = Task.Pattern.of(task);

                // create and execute the Program
                try {
                    final Program program = engine.createProgram(project, pattern);

                    final AssetCache cache = program.execute(shared);

                    // include the cachable results from the program cache into the shared cache
                    cache.invocables()
                            .filter(Invocable::isCacheable)
                            .map(Invocable.class::cast)
                            .forEach(invocable -> cache.get(invocable.getReference())
                                    .ifPresent(result -> shared.putIfAbsent(invocable, result)));
                } catch (final ProgramExecutionException e) {
                    System.err.printf("[spin] Program Execution Failed\n");
                    e.printStackTrace(System.err);

                    System.exit(-1);
                }
            });

            // close the Workspace AutoClosables
            workspace.close();

            System.err.printf("[spin] Program Execution Completed\n");
            System.exit(0);
        }
    }
}
