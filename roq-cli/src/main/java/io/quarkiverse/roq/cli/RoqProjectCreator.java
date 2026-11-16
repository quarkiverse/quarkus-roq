package io.quarkiverse.roq.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.DefaultArtifact;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.devtools.commands.CreateProject;
import io.quarkus.devtools.commands.CreateProjectHelper;
import io.quarkus.devtools.commands.data.QuarkusCommandOutcome;
import io.quarkus.devtools.project.BuildTool;
import io.quarkus.devtools.project.QuarkusProject;
import io.quarkus.devtools.project.QuarkusProjectHelper;
import io.quarkus.maven.utilities.MojoUtils;
import io.quarkus.registry.catalog.ExtensionCatalog;

/**
 * Programmatic API for creating Roq projects.
 * Can be used by other libraries or tools to scaffold Roq sites.
 */
public class RoqProjectCreator {

    public static final String ROQ_GROUP_ID = "io.quarkiverse.roq";
    public static final String ROQ_EXTENSION = ROQ_GROUP_ID + ":quarkus-roq";
    public static final String ROQ_PREFIX = ROQ_GROUP_ID + ":quarkus-roq-";
    public static final String ROQ_BOM_ARTIFACT_ID = "quarkus-roq-bom";
    public static final String ROQ_VERSION_PROPERTY = "quarkus-roq.version";

    private final Path projectDir;
    private final String artifactId;
    private String groupId = "io.acme";
    private String version = "1.0.0-SNAPSHOT";
    private String roqVersion;
    private BuildTool buildTool = BuildTool.MAVEN;
    private boolean noCode;
    private boolean noConfig;
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

    public RoqProjectCreator noConfig(boolean noConfig) {
        this.noConfig = noConfig;
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
        Set<String> allExtensions = new LinkedHashSet<>();
        allExtensions.add(withVersion(ROQ_EXTENSION));

        if (extensions != null) {
            for (String ext : extensions) {
                String resolved = resolveExtension(ext.trim());
                if (resolved != null) {
                    allExtensions.add(withVersion(resolved));
                }
            }
        }

        // Add default theme when no theme extension is specified
        if ((extensions == null || extensions.stream().noneMatch(e -> e.trim().startsWith("theme:")))
                && allExtensions.stream().noneMatch(e -> e.contains("roq-theme-"))) {
            allExtensions.add(withVersion(ROQ_PREFIX + "theme-default"));
        }

        Set<String> extraCodestarts = new LinkedHashSet<>();
        extraCodestarts.add("roq-project-codestart");
        if (allExtensions.stream().noneMatch(e -> e.contains("roq-theme-"))) {
            extraCodestarts.add("roq-base-theme-codestart");
        }

        ExtensionCatalog catalog = QuarkusProjectHelper.resolveExtensionCatalog();
        catalog = CreateProjectHelper.completeCatalog(catalog, allExtensions, QuarkusProjectHelper.artifactResolver());
        QuarkusProject qp = QuarkusProjectHelper.getProject(projectDir, catalog, buildTool);

        CreateProject createProject = new CreateProject(qp)
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .extensions(allExtensions)
                .extraCodestarts(extraCodestarts);

        if (!noConfig) {
            createProject.useConfigDir();
        }

        if (allExtensions.stream().noneMatch(e -> e.contains("roq-plugin-hybrid"))) {
            createProject.noDockerfiles();
        }

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
        deleteDirIfNoFiles(projectDir.resolve("src"));
        importRoqBom(projectDir.resolve("pom.xml"), QuarkusProjectHelper.artifactResolver());
    }

    /**
     * Import the Roq BOM in a Maven project and let it manage the versions of the Roq and Web Bundler dependencies,
     * so they cannot drift apart. Nothing is changed when the Roq version in use has no BOM.
     *
     * @return true if the pom was updated
     */
    static boolean importRoqBom(Path pomFile, MavenArtifactResolver resolver) throws IOException {
        if (!Files.isRegularFile(pomFile)) {
            return false;
        }
        Model model = MojoUtils.readPom(pomFile.toFile());
        String roqVersion = model.getDependencies().stream()
                .filter(d -> ROQ_EXTENSION.equals(d.getGroupId() + ":" + d.getArtifactId()))
                .map(Dependency::getVersion)
                .findFirst().orElse(null);
        if (roqVersion == null) {
            return false;
        }
        Set<String> managed = new HashSet<>();
        try {
            resolver.resolveDescriptor(new DefaultArtifact(ROQ_GROUP_ID, ROQ_BOM_ARTIFACT_ID, "pom", roqVersion))
                    .getManagedDependencies()
                    .forEach(d -> managed.add(d.getArtifact().getGroupId() + ":" + d.getArtifact().getArtifactId()));
        } catch (Exception e) {
            // Roq versions released before the BOM
            return false;
        }
        if (managed.isEmpty()) {
            return false;
        }

        model.getProperties().setProperty(ROQ_VERSION_PROPERTY, roqVersion);
        Dependency bom = new Dependency();
        bom.setGroupId(ROQ_GROUP_ID);
        bom.setArtifactId(ROQ_BOM_ARTIFACT_ID);
        bom.setVersion("${" + ROQ_VERSION_PROPERTY + "}");
        bom.setType("pom");
        bom.setScope("import");
        if (model.getDependencyManagement() == null) {
            model.setDependencyManagement(new DependencyManagement());
        }
        model.getDependencyManagement().getDependencies().add(0, bom);
        for (Dependency dependency : model.getDependencies()) {
            if (managed.contains(dependency.getGroupId() + ":" + dependency.getArtifactId())) {
                dependency.setVersion(null);
            }
        }
        MojoUtils.write(model, pomFile.toFile());
        return true;
    }

    private String withVersion(String gav) {
        String[] parts = gav.split(":");
        if (roqVersion != null && parts.length == 2 && parts[0].equals(ROQ_GROUP_ID)) {
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
            if (value.equals("theme:base")) {
                return null;
            }
            return ROQ_PREFIX + "theme-" + value.substring(6);
        }
        if (value.startsWith("plugin:")) {
            return ROQ_PREFIX + "plugin-" + value.substring(7);
        }
        if (value.startsWith("web:")) {
            return "io.quarkiverse.web-bundler:quarkus-web-bundler-" + value.substring(4);
        }
        return value;
    }

    private static void deleteDirIfNoFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.find(dir, Integer.MAX_VALUE, (p, a) -> a.isRegularFile())) {
            if (stream.findAny().isPresent()) {
                return;
            }
        }
        FileUtils.deleteDirectory(dir.toFile());
    }

}
