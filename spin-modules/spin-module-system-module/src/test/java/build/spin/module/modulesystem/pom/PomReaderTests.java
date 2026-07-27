package build.spin.module.modulesystem.pom;

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

import build.base.telemetry.TelemetryRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Targeted unit tests for {@link PomReader}.
 */
class PomReaderTests {

    private static final TelemetryRecorder RECORDER = mock(TelemetryRecorder.class);

    /**
     * An explicit {@code <scope>} on a {@code <dependency>} must win over
     * {@code <dependencyManagement>}, even when that explicit value happens to equal the Maven
     * default ({@code compile}).
     * <p>
     * Here the dependency explicitly declares {@code <scope>compile</scope>} while
     * {@code dependencyManagement} manages the same GA at {@code provided}. A prior version of
     * {@code PomReader} decided whether to apply the managed scope by comparing the
     * already-defaulted scope to the literal string {@code "compile"}, which conflated "no scope
     * declared" with "scope explicitly declared as compile" and incorrectly overrode the explicit
     * {@code compile} with the managed {@code provided}. {@link PomReader#toEffectiveDependency}
     * now checks the raw, pre-default scope text instead, so the explicit value is preserved.
     */
    @Test
    void read_honorsExplicitCompileScopeOverManagedScope(@TempDir final Path dir) throws Exception {
        final Path pomXml = dir.resolve("pom.xml");
        Files.writeString(pomXml, """
            <project>
              <groupId>com.example</groupId>
              <artifactId>consumer</artifactId>
              <version>1.0.0</version>
              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>com.example</groupId>
                    <artifactId>lib</artifactId>
                    <version>2.0.0</version>
                    <scope>provided</scope>
                  </dependency>
                </dependencies>
              </dependencyManagement>
              <dependencies>
                <dependency>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <scope>compile</scope>
                </dependency>
              </dependencies>
            </project>
            """);

        final PomReader reader = new PomReader(dir, RECORDER);
        final Optional<Pom> pom = reader.read(pomXml);

        assertThat(pom).isPresent();
        final Dependency lib = pom.get().dependencies().stream()
            .filter(d -> "lib".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();

        assertThat(lib.scope()).isEqualTo("compile");
    }

    /**
     * Same as {@link #read_honorsExplicitCompileScopeOverManagedScope} but for {@code <type>}: an
     * explicit {@code <type>jar</type>} must win over a differing managed type, even though
     * {@code "jar"} is also the Maven default applied when no {@code <type>} is declared at all.
     */
    @Test
    void read_honorsExplicitJarTypeOverManagedType(@TempDir final Path dir) throws Exception {
        final Path pomXml = dir.resolve("pom.xml");
        Files.writeString(pomXml, """
            <project>
              <groupId>com.example</groupId>
              <artifactId>consumer</artifactId>
              <version>1.0.0</version>
              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>com.example</groupId>
                    <artifactId>lib</artifactId>
                    <version>2.0.0</version>
                    <type>test-jar</type>
                  </dependency>
                </dependencies>
              </dependencyManagement>
              <dependencies>
                <dependency>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <type>jar</type>
                </dependency>
              </dependencies>
            </project>
            """);

        final PomReader reader = new PomReader(dir, RECORDER);
        final Optional<Pom> pom = reader.read(pomXml);

        assertThat(pom).isPresent();
        final Dependency lib = pom.get().dependencies().stream()
            .filter(d -> "lib".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();

        assertThat(lib.type()).isEqualTo("jar");
    }

    /**
     * {@code <artifactId>} must be interpolated like {@code <groupId>} and {@code <version>} are —
     * a pom that writes {@code <artifactId>${module.name}</artifactId>} should resolve it against
     * its own {@code <properties>}, not carry the literal {@code ${module.name}} through into the
     * effective pom's {@code artifactId()} and {@code project.artifactId} property.
     */
    @Test
    void read_interpolatesArtifactIdProperty(@TempDir final Path dir) throws Exception {
        final Path pomXml = dir.resolve("pom.xml");
        Files.writeString(pomXml, """
            <project>
              <groupId>com.example</groupId>
              <artifactId>${module.name}</artifactId>
              <version>1.0.0</version>
              <properties>
                <module.name>my-module</module.name>
              </properties>
            </project>
            """);

        final PomReader reader = new PomReader(dir, RECORDER);
        final Optional<Pom> pom = reader.read(pomXml);

        assertThat(pom).isPresent();
        assertThat(pom.get().artifactId()).isEqualTo("my-module");
        assertThat(pom.get().properties()).containsEntry("project.artifactId", "my-module");
    }

    /**
     * A plugin {@code <dependency>} redeclared under {@code <build><plugins>} with only its GA (no
     * {@code <version>}), to inherit the version from the matching
     * {@code <pluginManagement><plugins>} entry, should end up with that managed version —
     * mirroring real Maven's field-level dependency merge (see
     * {@link #read_honorsExplicitCompileScopeOverManagedScope} for the project-dependency
     * equivalent).
     */
    @Test
    void read_pluginDependencyShouldInheritManagedVersion(@TempDir final Path dir) throws Exception {
        final Path pomXml = dir.resolve("pom.xml");
        Files.writeString(pomXml, """
            <project>
              <groupId>com.example</groupId>
              <artifactId>consumer</artifactId>
              <version>1.0.0</version>
              <build>
                <pluginManagement>
                  <plugins>
                    <plugin>
                      <groupId>org.apache.maven.plugins</groupId>
                      <artifactId>maven-checkstyle-plugin</artifactId>
                      <dependencies>
                        <dependency>
                          <groupId>com.example</groupId>
                          <artifactId>custom-checks</artifactId>
                          <version>2.0.0</version>
                        </dependency>
                      </dependencies>
                    </plugin>
                  </plugins>
                </pluginManagement>
                <plugins>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-checkstyle-plugin</artifactId>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>custom-checks</artifactId>
                      </dependency>
                    </dependencies>
                  </plugin>
                </plugins>
              </build>
            </project>
            """);

        final PomReader reader = new PomReader(dir, RECORDER);
        final Optional<Pom> pom = reader.read(pomXml);

        assertThat(pom).isPresent();
        final Plugin checkstylePlugin = pom.get()
            .plugin(new GA("org.apache.maven.plugins", "maven-checkstyle-plugin"))
            .orElseThrow();
        final Dependency customChecks = checkstylePlugin.dependencies().stream()
            .filter(d -> "custom-checks".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();

        assertThat(customChecks.version()).contains("2.0.0");
    }
}
