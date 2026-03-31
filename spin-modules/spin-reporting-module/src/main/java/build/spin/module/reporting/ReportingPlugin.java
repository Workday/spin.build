package build.spin.module.reporting;

/*-
 * #%L
 * Spin Reporting Module
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

import build.base.configuration.Configuration;
import build.spin.Instruction;
import build.spin.Plugin;
import build.spin.Project;
import build.spin.Reference;
import build.spin.Task;
import build.spin.annotation.Automatic;
import build.spin.annotation.NoCache;
import build.spin.common.DefaultProgram;
import build.spin.option.BuildDirectoryName;
import build.spin.option.EngineVersion;
import jakarta.inject.Inject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link Plugin} for generating reporting information for a {@link Project}.
 */
public class ReportingPlugin
    implements Plugin {

    /**
     * A {@link Task} to render the {@link Instruction}s in a {@link DefaultProgram}.
     */
    @Automatic
    @NoCache
    public static class Render
        implements Task<Path> {

        @Inject
        private EngineVersion engineVersion;

        @Inject
        private Project project;

        @Inject
        private Configuration optionsByType;

        /**
         * Renders the {@link Instruction}s in the {@link DefaultProgram}.
         * <p>
         * Based on the example here:
         * https://github.com/vasturiano/3d-force-graph/blob/master/example/directional-links-arrows/index.html
         *
         * @param instructions the {@link Instruction}s for the {@link DefaultProgram}
         * @throws IOException should the rendering fail
         * @return the {@link Path} to the generated report
         */
        public Path render(final Stream<Instruction<?>> instructions)
            throws IOException {

            // only render the Instructions when there's a place (Build Directory) in which to store it
            final String buildPath = this.optionsByType.getValue(BuildDirectoryName.class);

            // ensure the build path exists
            final Path projectPath = this.project.path();

            // determine the Task.Pattern from which we can generate the report name
            final String pattern = this.optionsByType.getOptional(Task.Pattern.class)
                .orElse(Task.Pattern.of("default"))
                .get();

            // santitize the pattern, so it can be used in a file name
            final StringBuilder reportNameBuilder = new StringBuilder(pattern.length());
            for (int i = 0; i < pattern.length(); i++) {
                final char c = pattern.charAt(i);

                if (Character.isAlphabetic(c) || Character.isDigit(c) || c == '.' || c == '-') {
                    reportNameBuilder.append(c);
                }
                else if (reportNameBuilder.length() > 0
                    && reportNameBuilder.charAt(reportNameBuilder.length() - 1) != '-') {
                    reportNameBuilder.append('-');
                }
            }

            final String programName = reportNameBuilder.toString();

            if (reportNameBuilder.length() > 0 && reportNameBuilder.charAt(reportNameBuilder.length() - 1) != '-') {
                reportNameBuilder.append('-');
            }
            reportNameBuilder.append("report.html");

            // establish location of the Report to be generated
            final Path reportPath = projectPath.resolve(buildPath).resolve(reportNameBuilder.toString());

            if (Files.exists(projectPath.resolve(buildPath))) {

                // collect and index the Instructions by Task.Reference as we'll be using them a few times
                // (but exclude this Task as we don't want to render it)
                final LinkedHashMap<Reference, Instruction<?>> instructionMap = new LinkedHashMap<>();
                instructions
                    .filter(instruction -> !instruction.getInvocable().getTaskClass().equals(getClass()))
                    .forEach(instruction -> instructionMap.put(instruction.getReference(), instruction));

                final StringBuilder builder = new StringBuilder();

                // include the header
                builder.append("<head>\n"
                    + "  <style> body { margin: 0; } </style>\n"
                    + "\n"
                    + "  <script src=\"https://unpkg.com/3d-force-graph\"></script>\n"
                    + "  <title>spin " + this.engineVersion.get() + " program: " + programName + "</title>\n"
                    + "</head>\n"
                    + "\n"
                    + "<body>\n"
                    + "  <div id=\"3d-graph\"></div>\n"
                    + "\n"
                    + "  <script>"
                    + "");

                // output the json to build the graph
                builder.append("const gData = {\n");
                builder.append("  nodes: [\n");

                builder.append(instructionMap.values().stream()
                    .map(instruction -> "{ \"id\": \"" + instruction.getIdentity() + "\", "
                        + "\"project\": \"" + instruction.getInvocable().getProject().name() + "\", "
                        + "\"task\": \"" + instruction.getInvocable().getProject().name() + ":"
                        + instruction.getInvocable().getPlugin().name() + ":"
                        + instruction.getInvocable().getTaskName() + "\""
                        + "}")
                    .collect(Collectors.joining(",\n")));

                builder.append("  ],\n");
                builder.append("  links: [\n");

                builder.append(instructionMap.values().stream()
                    .flatMap(instruction ->
                        instruction.dependencies()
                            .map(instructionMap::get)
                            .filter(Objects::nonNull)
                            .map(dependency -> "{ \"source\": \"" + dependency.getIdentity() + "\","
                                + "\"target\": \"" + instruction.getIdentity() + "\"}"))
                    .collect(Collectors.joining(",\n")));

                builder.append("  ]\n");
                builder.append("};\n");

                // include the footer
                builder.append("\n"
                    + "    const Graph = ForceGraph3D()\n"
                    + "    (document.getElementById('3d-graph'))\n"
                    + "      .graphData(gData)\n"
                    + "      .nodeLabel('task')\n"
                    + "      .nodeAutoColorBy('project')\n"
                    + "      .linkDirectionalArrowLength(3.5)\n"
                    + "      .linkDirectionalArrowRelPos(1)\n"
                    + "      .linkCurvature(0.25);\n"
                    + "  </script>\n"
                    + "</body>");

                final BufferedWriter writer = Files.newBufferedWriter(reportPath);
                writer.write(builder.toString());
                writer.close();
            }

            return reportPath;
        }
    }

    /**
     * The {@link Plugin.MetaClass} for {@link ReportingPlugin}.
     */
    public static class MetaClass
        implements Plugin.MetaClass {

        @Override
        public boolean isDetectedIn(final Path path) {
            // reporting is only detected when a Project has been detected
            return false;
        }

        @Override
        public boolean isDetectedIn(final Project project) {
            // reporting is always available for all Projects
            return true;
        }
    }
}
