package io.quarkiverse.roq.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;

import io.quarkiverse.roq.migration.engine.MigrationException;
import io.quarkiverse.roq.migration.engine.Scaffolder;
import io.quarkiverse.roq.migration.engine.TargetSite;

/**
 * Creates the build of a migrated site: a Roq project with the extensions the migration found it needs, generated as
 * {@code roq create} does in a temporary directory. Only {@link #FILES_COPIED_FROM_GENERATED_PROJECT} are kept from
 * it; the migrated site brings its own content, templates and theme.
 */
class MigrationScaffolder implements Scaffolder {

    /**
     * The files that make the generated project a Roq project: the build and its wrapper, for Maven or Gradle, and the
     * configuration, without which Roq does not build. The ones the generated project does not have are skipped, and a
     * migrated site keeps its own copy of the ones it has. Quarkus has no public names for the wrappers.
     */
    static final List<String> FILES_COPIED_FROM_GENERATED_PROJECT = Stream.concat(
            RoqMain.BUILD_TOOLS.stream().flatMap(tool -> Stream.of(tool.getBuildFiles())),
            Stream.of("mvnw", "mvnw.cmd", ".mvn", "gradlew", "gradlew.bat", "gradle", "config/application.properties"))
            .distinct().toList();

    @Override
    public void scaffold(TargetSite site) throws Exception {
        Path dir = site.dir();
        // A site that has a build already would have it replaced
        for (String file : RoqMain.BUILD_FILES) {
            if (Files.exists(dir.resolve(file))) {
                throw new MigrationException(dir.resolve(file) + " exists already; the migration writes its own");
            }
        }
        String name = site.analysis().projectName() != null ? site.analysis().projectName()
                : dir.getFileName().toString();
        // theme:base: no theme, so the site's own layouts are the ones used
        List<String> extensions = new ArrayList<>(List.of("theme:base"));
        extensions.addAll(site.analysis().extensions());

        Path tmp = Files.createTempDirectory("roq-migrate");
        try {
            Path project = tmp.resolve(name);
            boolean created = new RoqProjectCreator(project, name)
                    .noCode(true)
                    .extensions(extensions)
                    .create();
            if (!created) {
                throw new MigrationException("Could not create the Roq project " + name);
            }
            for (String file : FILES_COPIED_FROM_GENERATED_PROJECT) {
                copyIfAbsent(project.resolve(file), dir.resolve(file));
            }
            site.report().info("Created the Roq project " + name + " with " + String.join(", ", extensions));
        } finally {
            FileUtils.deleteDirectory(tmp.toFile());
        }
    }

    private static void copyIfAbsent(Path from, Path to) throws IOException {
        if (!Files.exists(from) || Files.exists(to)) {
            return;
        }
        if (Files.isDirectory(from)) {
            FileUtils.copyDirectory(from.toFile(), to.toFile());
        } else {
            Files.createDirectories(to.getParent());
            Files.copy(from, to);
            to.toFile().setExecutable(from.toFile().canExecute());
        }
    }
}
