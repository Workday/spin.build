package build.spin.module.gpg;

/*-
 * #%L
 * Spin GPG Module
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

import build.base.configuration.ConfigurationBuilder;
import build.base.io.PathSet;
import build.base.telemetry.TelemetryRecorder;
import build.spawn.application.Application;
import build.spawn.application.option.Argument;
import build.spawn.application.option.Executable;
import build.spawn.application.option.Name;
import build.spawn.application.option.StandardErrorSubscriber;
import build.spawn.platform.local.LocalMachine;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Task;
import build.spin.annotation.Description;
import build.spin.common.ProcessRunner;
import build.spin.module.configuration.Configuration;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A {@link Plugin}, detected for a {@link Project} whenever a {@link SignableResource} is present, providing the
 * on-demand {@code sign} {@link Task} that GPG-signs the artifacts included via {@link SignableResource#include(Path)}.
 *
 * @author brian.oliver
 * @since Jul-2026
 */
public class GpgPlugin
    implements Plugin {

    /**
     * A {@link Task} that GPG-signs the artifacts included in the {@link SignableResource} for the {@link Project},
     * in a single {@code gpg} invocation, using {@code --multifile}, returning a {@link PathSet} of the
     * {@link Path}s to the resulting detached signature files (one per signed artifact).
     * <p>
     * This {@link Task} is deliberately not {@link build.spin.annotation.Automatic}: signing only occurs when
     * explicitly requested (eg: {@code spin sign}), never as part of a regular build.
     * <p>
     * Two configuration keys (resolved from {@code .spin} configuration) are supported:
     * <ul>
     *     <li>{@code passphrase} - the passphrase for the signing key, supplied to {@code gpg} via
     *     {@code --pinentry-mode loopback --passphrase}.  When absent, {@code gpg} is invoked without a
     *     passphrase (eg: relying on {@code gpg-agent} caching).  Note that this option passes the passphrase
     *     as a command-line argument, which may be visible to other processes on the same machine (eg: via
     *     {@code ps}).</li>
     *     <li>{@code armor} - a {@code boolean} indicating whether ASCII-armored ({@code .asc}) signatures
     *     should be produced instead of binary ({@code .sig}) ones.  Defaults to {@code true}.</li>
     * </ul>
     */
    @Named("sign")
    @Description("GPG-sign the artifacts included for signing")
    public static class Sign
        implements Task<PathSet> {

        @Inject
        private TelemetryRecorder recorder;

        @Inject
        private LocalMachine machine;

        @Inject
        private SignableResource signableResource;

        @Inject
        private SigningService signingService;

        @Inject
        @Configuration
        @Named("passphrase")
        private Optional<String> passphrase;

        @Inject
        @Configuration
        @Named("armor")
        private Optional<Boolean> armor;

        /**
         * GPG-signs the artifacts included in the {@link SignableResource}, in a single {@code gpg} invocation.
         *
         * @return a {@link PathSet} of the {@link Path}s to the detached signature files created, one per signed
         *         artifact, or {@link PathSet#empty()} when no artifacts have been included for signing
         * @throws Exception should launching or waiting on the {@code gpg} process fail, or {@code gpg} exit
         *                    with a non-zero exit code
         */
        public PathSet sign()
            throws Exception {

            final var artifacts = this.signableResource.artifacts();

            if (artifacts.isEmpty()) {
                this.recorder.info("No artifacts included for signing");
                return PathSet.empty();
            }

            final var armorEnabled = this.armor.orElse(true);

            final var configuration = ConfigurationBuilder.create()
                .add(Executable.of("gpg"))
                .add(Name.of("gpg"))
                .add(Argument.of("--homedir"))
                .add(Argument.of(this.signingService.configurationPath().toString()))
                .add(Argument.of("--batch"))
                .add(Argument.of("--yes"));

            this.passphrase.ifPresent(value -> configuration
                .add(Argument.of("--pinentry-mode"))
                .add(Argument.of("loopback"))
                .add(Argument.of("--passphrase"))
                .add(Argument.of(value)));

            configuration
                .add(Argument.of("--multifile"))
                .add(Argument.of("--detach-sign"));

            if (armorEnabled) {
                configuration.add(Argument.of("--armor"));
            }

            artifacts.stream()
                .map(Path::toString)
                .map(Argument::of)
                .forEach(configuration::add);

            // gpg writes progress to stderr on a reader thread, so accumulate it in a thread-safe
            // structure; it is joined into the failure output only if the process fails
            final var capturedErrors = new ConcurrentLinkedQueue<String>();
            configuration.add(StandardErrorSubscriber.of(line -> {
                this.recorder.warn(line);
                capturedErrors.add(line);
            }));

            final var activity = this.recorder.commence("Signing %d artifact(s)", artifacts.count());
            try (var gpg = this.machine.launch(Application.class, configuration)) {
                ProcessRunner.await(gpg, "GPG Signing", () -> String.join("\n", capturedErrors));
                activity.complete();
            }
            catch (final Exception e) {
                activity.completeExceptionally(e);
                this.recorder.error(e, "Failed to GPG-sign artifacts");
                throw e;
            }

            final var signatureExtension = armorEnabled ? ".asc" : ".sig";

            return artifacts.stream()
                .map(artifact -> artifact.resolveSibling(artifact.getFileName() + signatureExtension))
                .collect(PathSet.collector());
        }
    }

    /**
     * The {@link Plugin.MetaClass} for {@link GpgPlugin}.
     */
    public static class MetaClass
        implements Plugin.MetaClass {

        @Override
        public boolean isDetectedIn(final Path path) {
            return false;
        }

        @Override
        public boolean isDetectedIn(final Project project) {
            return project.getResource(SignableResource.class).isPresent();
        }
    }
}
