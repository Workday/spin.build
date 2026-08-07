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
import build.base.telemetry.Activity;
import build.base.telemetry.TelemetryRecorder;
import build.codemodel.dependency.injection.InjectionFramework;
import build.codemodel.dependency.injection.TypeLiteral;
import build.spawn.application.Application;
import build.spawn.application.option.Argument;
import build.spawn.platform.local.LocalMachine;
import build.spin.Project;
import build.spin.module.configuration.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GpgPlugin}.
 *
 * @author brian.oliver
 * @since Jul-2026
 */
class GpgPluginTests {

    /**
     * Ensure the {@link GpgPlugin.MetaClass} never detects a {@link Path}-based {@link Project} directly.
     */
    @Test
    void shouldNotDetectByPath() {
        final var metaClass = new GpgPlugin.MetaClass();

        assertThat(metaClass.isDetectedIn(Paths.get(".")))
            .isFalse();
    }

    /**
     * Ensure the {@link GpgPlugin.MetaClass} detects a {@link Project} whenever a {@link SignableResource} is
     * present for it.
     */
    @Test
    void shouldDetectWhenSignableResourceIsPresent() {
        final var project = mock(Project.class);
        when(project.getResource(SignableResource.class))
            .thenReturn(Optional.of(new SignableResource()));

        final var metaClass = new GpgPlugin.MetaClass();

        assertThat(metaClass.isDetectedIn(project))
            .isTrue();
    }

    /**
     * Ensure the {@link GpgPlugin.MetaClass} doesn't detect a {@link Project} when no {@link SignableResource} is
     * present for it.
     */
    @Test
    void shouldNotDetectWhenSignableResourceIsAbsent() {
        final var project = mock(Project.class);
        when(project.getResource(SignableResource.class))
            .thenReturn(Optional.empty());

        final var metaClass = new GpgPlugin.MetaClass();

        assertThat(metaClass.isDetectedIn(project))
            .isFalse();
    }

    /**
     * Ensure {@link GpgPlugin.Sign} doesn't launch {@code gpg} at all when no artifacts have been included for
     * signing.
     */
    @Test
    void shouldNotLaunchGpgWhenNoArtifactsAreIncluded()
        throws Exception {

        final var recorder = mock(TelemetryRecorder.class);
        final var machine = mock(LocalMachine.class);
        final var signable = new SignableResource();
        final var signingService = mock(SigningService.class);
        when(signingService.configurationPath())
            .thenReturn(Paths.get(".gnupg"));

        final var context = InjectionFramework.create().newContext();
        context.bind(TelemetryRecorder.class).to(recorder);
        context.bind(LocalMachine.class).to(machine);
        context.bind(SignableResource.class).to(signable);
        context.bind(SigningService.class).to(signingService);
        context.bind(new TypeLiteral<Optional<String>>() {
        }).as("passphrase").with(Configuration.class).to(Optional.empty());
        context.bind(new TypeLiteral<Optional<Boolean>>() {
        }).as("armor").with(Configuration.class).to(Optional.empty());

        final var sign = context.create(GpgPlugin.Sign.class);

        assertThat(sign.sign().isEmpty())
            .isTrue();
        verifyNoInteractions(machine);
    }

    /**
     * Ensure {@link GpgPlugin.Sign} produces ASCII-armored ({@code .asc}) signatures, and includes {@code --armor}
     * in the {@code gpg} invocation, by default (when no {@code armor} configuration is present).
     */
    @Test
    void shouldDefaultToArmoredSignatures()
        throws Exception {

        final var artifact = Paths.get("artifact.jar");
        final var signable = new SignableResource().include(artifact);

        final var configurationCaptor = ArgumentCaptor.forClass(ConfigurationBuilder.class);
        final var sign = createSign(signable, configurationCaptor, Optional.empty(), Optional.empty());

        assertThat(sign.sign().stream().toList())
            .containsExactly(Paths.get("artifact.jar.asc"));

        assertThat(argumentsOf(configurationCaptor))
            .contains("--armor");
    }

    /**
     * Ensure {@link GpgPlugin.Sign} produces binary ({@code .sig}) signatures, and omits {@code --armor} from the
     * {@code gpg} invocation, when {@code armor} is explicitly disabled via configuration.
     */
    @Test
    void shouldProduceBinarySignaturesWhenArmorDisabled()
        throws Exception {

        final var artifact = Paths.get("artifact.jar");
        final var signable = new SignableResource().include(artifact);

        final var configurationCaptor = ArgumentCaptor.forClass(ConfigurationBuilder.class);
        final var sign = createSign(signable, configurationCaptor, Optional.empty(), Optional.of(false));

        assertThat(sign.sign().stream().toList())
            .containsExactly(Paths.get("artifact.jar.sig"));

        assertThat(argumentsOf(configurationCaptor))
            .doesNotContain("--armor");
    }

    /**
     * Ensure {@link GpgPlugin.Sign} includes {@code --pinentry-mode loopback --passphrase <value>} in the
     * {@code gpg} invocation when a {@code passphrase} is configured.
     */
    @Test
    void shouldIncludePassphraseArgumentsWhenConfigured()
        throws Exception {

        final var artifact = Paths.get("artifact.jar");
        final var signable = new SignableResource().include(artifact);

        final var configurationCaptor = ArgumentCaptor.forClass(ConfigurationBuilder.class);
        final var sign = createSign(signable, configurationCaptor, Optional.of("secret"), Optional.empty());

        sign.sign();

        assertThat(argumentsOf(configurationCaptor))
            .containsSequence("--pinentry-mode", "loopback", "--passphrase", "secret");
    }

    /**
     * Ensure {@link GpgPlugin.Sign} omits any passphrase-related arguments from the {@code gpg} invocation when
     * no {@code passphrase} is configured.
     */
    @Test
    void shouldNotIncludePassphraseArgumentsWhenNotConfigured()
        throws Exception {

        final var artifact = Paths.get("artifact.jar");
        final var signable = new SignableResource().include(artifact);

        final var configurationCaptor = ArgumentCaptor.forClass(ConfigurationBuilder.class);
        final var sign = createSign(signable, configurationCaptor, Optional.empty(), Optional.empty());

        sign.sign();

        assertThat(argumentsOf(configurationCaptor))
            .doesNotContain("--pinentry-mode", "--passphrase");
    }

    /**
     * Creates a {@link GpgPlugin.Sign} whose injected {@link LocalMachine} is mocked to successfully "launch"
     * {@code gpg}, capturing the {@link ConfigurationBuilder} used via the provided {@link ArgumentCaptor}.
     *
     * @param signable             the {@link SignableResource}
     * @param configurationCaptor  the {@link ArgumentCaptor} to capture the {@link ConfigurationBuilder} used
     * @param passphrase           the {@code passphrase} configuration value
     * @param armor                the {@code armor} configuration value
     * @return a new {@link GpgPlugin.Sign}
     */
    private GpgPlugin.Sign createSign(final SignableResource signable,
                                       final ArgumentCaptor<ConfigurationBuilder> configurationCaptor,
                                       final Optional<String> passphrase,
                                       final Optional<Boolean> armor) {

        final var activity = mock(Activity.class);
        final var recorder = mock(TelemetryRecorder.class);
        when(recorder.commence(anyString(), any()))
            .thenReturn(activity);

        final var application = mock(Application.class);
        doReturn(CompletableFuture.completedFuture(application))
            .when(application).onExit();
        when(application.exitValue())
            .thenReturn(OptionalInt.of(0));

        final var machine = mock(LocalMachine.class);
        when(machine.launch(eq(Application.class), configurationCaptor.capture()))
            .thenReturn(application);

        final var signingService = mock(SigningService.class);
        when(signingService.configurationPath())
            .thenReturn(Paths.get(".gnupg"));

        final var context = InjectionFramework.create().newContext();
        context.bind(TelemetryRecorder.class).to(recorder);
        context.bind(LocalMachine.class).to(machine);
        context.bind(SignableResource.class).to(signable);
        context.bind(SigningService.class).to(signingService);
        context.bind(new TypeLiteral<Optional<String>>() {
        }).as("passphrase").with(Configuration.class).to(passphrase);
        context.bind(new TypeLiteral<Optional<Boolean>>() {
        }).as("armor").with(Configuration.class).to(armor);

        return context.create(GpgPlugin.Sign.class);
    }

    /**
     * Obtains the {@code gpg} {@link Argument} values captured by the provided {@link ArgumentCaptor}, in order.
     *
     * @param configurationCaptor the {@link ArgumentCaptor} that captured the {@link ConfigurationBuilder} used
     * @return the {@link Argument} values, in order
     */
    private static List<String> argumentsOf(final ArgumentCaptor<ConfigurationBuilder> configurationCaptor) {
        return configurationCaptor.getValue()
            .stream(Argument.class)
            .map(Argument::get)
            .toList();
    }
}
