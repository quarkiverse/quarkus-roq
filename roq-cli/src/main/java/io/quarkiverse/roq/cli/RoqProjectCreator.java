package io.quarkiverse.roq.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.quarkus.devtools.commands.CreateProject;
import io.quarkus.devtools.commands.data.QuarkusCommandOutcome;
import io.quarkus.devtools.project.BuildTool;
import io.quarkus.devtools.project.QuarkusProject;
import io.quarkus.devtools.project.QuarkusProjectHelper;

/**
 * Programmatic API for creating Roq projects.
 * Can be used by other libraries or tools to scaffold Roq sites.
 */
public class RoqProjectCreator {

    public static final String ROQ_GROUP_ID = "io.quarkiverse.roq";
    public static final String ROQ_EXTENSION = ROQ_GROUP_ID + ":quarkus-roq";
    public static final String ROQ_PREFIX = ROQ_GROUP_ID + ":quarkus-roq-";

    private final Path projectDir;
    private final String artifactId;
    private String groupId = "io.acme";
    private String version = "1.0.0-SNAPSHOT";
    private String roqVersion;
    private BuildTool buildTool = BuildTool.MAVEN;
    private boolean noCode;
    private List<String> extensions;

    public RoqProjectCreator(Path projectDir, String artifactId) {
        this.projectDir = projectDir;
        this.artifactId = artifactId;
    }

    public RoqProjectCreator groupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public RoqProjectCreator version(String version) {
        this.version = version;
        return this;
    }

    public RoqProjectCreator roqVersion(String roqVersion) {
        this.roqVersion = roqVersion;
        return this;
    }

    public RoqProjectCreator buildTool(BuildTool buildTool) {
        this.buildTool = buildTool;
        return this;
    }

    public RoqProjectCreator noCode(boolean noCode) {
        this.noCode = noCode;
        return this;
    }

    public RoqProjectCreator extensions(List<String> extensions) {
        this.extensions = extensions;
        return this;
    }

    /**
     * Create the Roq project.
     *
     * @return true if the project was created successfully
     */
    public boolean create() throws Exception {
        Set<String> allExtensions = new HashSet<>();
        allExtensions.add(withVersion(ROQ_EXTENSION));

        if (extensions != null) {
            for (String ext : extensions) {
                allExtensions.add(withVersion(resolveExtension(ext.trim())));
            }
        }

        QuarkusProject qp = QuarkusProjectHelper.getProject(projectDir, buildTool);

        CreateProject createProject = new CreateProject(qp)
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .extensions(allExtensions);

        if (noCode) {
            createProject.noCode();
        }

        QuarkusCommandOutcome outcome = createProject.execute();

        if (outcome.isSuccess()) {
            postCreate();
            return true;
        }
        return false;
    }

    private void postCreate() throws IOException {
        // Move application.properties to config/
        Path propsSource = projectDir.resolve("src/main/resources/application.properties");
        Path configDir = projectDir.resolve("config");
        Files.createDirectories(configDir);
        Path propsDest = configDir.resolve("application.properties");
        if (Files.exists(propsSource)) {
            Files.move(propsSource, propsDest);
        } else {
            Files.createFile(propsDest);
        }

        // Remove src/ and docker files (not needed for a Roq site)
        deleteDir(projectDir.resolve("src"));
        Files.deleteIfExists(projectDir.resolve(".dockerignore"));
    }

    private String withVersion(String gav) {
        if (roqVersion != null && gav.startsWith(ROQ_GROUP_ID + ":")) {
            return gav + ":" + roqVersion;
        }
        return gav;
    }

    /**
     * Resolve a short extension name to a full GAV coordinate.
     * Supports: "theme:default", "plugin:tagging", "web:sass", or full GAV.
     */
    public static String resolveExtension(String value) {
        if (value.contains(":") && !value.startsWith("theme:") && !value.startsWith("plugin:")
                && !value.startsWith("web:")) {
            return value;
        }
        if (value.startsWith("theme:")) {
            return ROQ_PREFIX + "theme-" + value.substring(6);
        }
        if (value.startsWith("plugin:")) {
            return ROQ_PREFIX + "plugin-" + value.substring(7);
        }
        if (value.startsWith("web:")) {
            return "io.quarkiverse.web-bundler:quarkus-web-bundler-" + value.substring(4);
        }
        return ROQ_PREFIX + value;
    }

    private static void deleteDir(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }
    }
}