package io.quarkiverse.roq.migration.dev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.roq.migration.SiteFiles;
import io.quarkiverse.roq.migration.engine.MigrationException;
import io.quarkiverse.roq.migration.engine.ProjectDependency;
import io.quarkiverse.roq.migration.engine.Scaffolder;
import io.quarkiverse.roq.migration.engine.TargetSite;

/**
 * A skeleton of the scaffolder {@code roq migrate} will use: create the Roq project with the analysed extensions, then
 * add the analysed dependencies.
 * <p>
 * Until it moves to the Roq CLI, it runs the {@code roq create} of a locally built CLI jar and edits the pom.xml as
 * text. The CLI's version will create the project with {@code RoqProjectCreator} and add the dependencies with the
 * Quarkus devtools, which also handle Gradle projects.
 */
class RoqScaffolder implements Scaffolder {

    private final Path repo;

    /**
     * @param repo the quarkus-roq checkout with the built CLI jar and the parent pom
     */
    RoqScaffolder(Path repo) {
        this.repo = repo;
    }

    @Override
    public void scaffold(TargetSite site) throws Exception {
        createProject(site);
        Path pom = site.dir().resolve("pom.xml");
        if (!site.analysis().dependencies().isEmpty()) {
            if (addDependencies(pom, site.analysis().dependencies())) {
                site.report().info("Added the dependencies " + site.analysis().dependencies());
            } else {
                site.report().todo("Add these dependencies to pom.xml: " + site.analysis().dependencies());
            }
        }
    }

    /**
     * Runs {@code roq create} into a temporary directory and keeps its pom.xml, as roq-it-jekyll does.
     */
    private void createProject(TargetSite site) throws Exception {
        Path cli = repo.resolve("roq-cli/target/quarkus-roq-cli-999-SNAPSHOT-runner.jar");
        if (!Files.isRegularFile(cli)) {
            throw new MigrationException("Build the Roq CLI first: mvn -pl roq-cli install -DskipTests");
        }
        Path tmp = Files.createTempDirectory("roq-create");
        try {
            String name = site.analysis().projectName();
            List<String> extensions = new ArrayList<>(List.of("theme:base"));
            extensions.addAll(site.analysis().extensions());
            run(tmp, "java", "-jar", cli.toString(), "create", name, "--no-code", "-x", String.join(",", extensions),
                    "--roq-version", "999-SNAPSHOT");
            String pom = Files.readString(tmp.resolve(name).resolve("pom.xml"));
            Files.writeString(site.dir().resolve("pom.xml"), withSnapshotProperties(pom));
            site.report().info("Created pom.xml for " + name);
        } finally {
            SiteFiles.deleteTree(tmp);
        }
    }

    /**
     * Add the dependencies to the project's own dependencies, not its dependency management, once each.
     *
     * @return false if the pom.xml has no dependencies section to add them to
     */
    static boolean addDependencies(Path pom, Collection<ProjectDependency> dependencies) throws IOException {
        String xml = Files.readString(pom);
        int management = xml.indexOf("</dependencyManagement>");
        int end = xml.indexOf("</dependencies>", Math.max(management, 0));
        if (end < 0) {
            return false;
        }
        int lineStart = xml.lastIndexOf('\n', end) + 1;
        String indent = xml.substring(lineStart, end).replaceAll("\\S.*", "");
        String inner = indent + "        ";
        StringBuilder entries = new StringBuilder();
        for (ProjectDependency dependency : dependencies) {
            if (xml.contains("<groupId>" + dependency.groupId() + "</groupId>")
                    && xml.contains("<artifactId>" + dependency.artifactId() + "</artifactId>")) {
                continue;
            }
            entries.append(indent).append("    <dependency>\n")
                    .append(inner).append("<groupId>").append(dependency.groupId()).append("</groupId>\n")
                    .append(inner).append("<artifactId>").append(dependency.artifactId()).append("</artifactId>\n")
                    .append(inner).append("<version>").append(dependency.version()).append("</version>\n");
            if (dependency.scope() != null) {
                entries.append(inner).append("<scope>").append(dependency.scope()).append("</scope>\n");
            }
            entries.append(indent).append("    </dependency>\n");
        }
        Files.writeString(pom, xml.substring(0, lineStart) + entries + xml.substring(lineStart));
        return true;
    }

    /**
     * Roq's 999-SNAPSHOT poms use version properties that only resolve through this repository's parent pom.
     */
    private String withSnapshotProperties(String pom) throws IOException {
        String parent = Files.readString(repo.resolve("pom.xml"));
        StringBuilder properties = new StringBuilder();
        for (String property : List.of("quarkus-web-bundler.version", "asciidoctorj-diagram.version")) {
            Matcher value = Pattern.compile("<" + Pattern.quote(property) + ">([^<]+)</").matcher(parent);
            if (value.find()) {
                properties.append("        <").append(property).append(">").append(value.group(1))
                        .append("</").append(property).append(">\n");
            }
        }
        return pom.replaceFirst("(\\s*)</properties>", "\n" + Matcher.quoteReplacement(properties.toString())
                + "    </properties>");
    }

    private static void run(Path dir, String... command) throws IOException, InterruptedException, MigrationException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile()).inheritIO();
        // roq create reads the Maven command line from the environment; don't hand it the one running us
        builder.environment().keySet().removeIf(name -> name.startsWith("MAVEN_"));
        Process process = builder.start();
        if (process.waitFor() != 0) {
            throw new MigrationException(String.join(" ", command) + " failed");
        }
    }
}
