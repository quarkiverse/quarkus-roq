package io.quarkiverse.roq.migration.dev;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.roq.migration.engine.MigrationException;
import io.quarkiverse.roq.migration.engine.MigrationOptions;
import io.quarkiverse.roq.migration.engine.MigrationOutput;
import io.quarkiverse.roq.migration.engine.MigrationRunner;
import io.quarkiverse.roq.migration.engine.SiteBuilder;
import io.quarkiverse.roq.migration.engine.SiteMigrator;
import io.quarkiverse.roq.migration.engine.SiteType;
import io.quarkiverse.roq.migration.gatsby.GatsbyMigrator;

/**
 * Runs a migration from a checkout of this repository, until {@code roq migrate} exists. Not shipped: it lives in the
 * test sources.
 *
 * <pre>
 * mvn -pl migration test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.quarkiverse.roq.migration.dev.MigrateDev \
 *     -Dexec.args="/path/to/site /path/to/output [--no-build]"
 * </pre>
 *
 * The Roq project is created by {@link RoqScaffolder}, with the {@code roq create} of a locally built Roq CLI
 * ({@code roq-cli/target/quarkus-roq-cli-999-SNAPSHOT-runner.jar}), for Roq 999-SNAPSHOT, and the site is built with
 * Maven.
 */
public class MigrateDev {

    public static void main(String... args) throws Exception {
        List<String> positional = new ArrayList<>();
        boolean build = true;
        for (String arg : args) {
            if (arg.equals("--no-build")) {
                build = false;
            } else {
                positional.add(arg);
            }
        }
        if (positional.size() != 2) {
            System.err.println("Usage: MigrateDev <site> <output-dir> [--no-build]");
            System.exit(2);
        }
        Path source = Path.of(positional.get(0)).toAbsolutePath().normalize();
        Path output = Path.of(positional.get(1)).toAbsolutePath().normalize();
        Path repo = findRepositoryRoot();

        SiteMigrator migrator = migratorFor(source);
        MigrationOutput console = new MigrationOutput() {
            @Override
            public void info(String message) {
                System.out.println(message);
            }

            @Override
            public void warn(String message) {
                System.out.println("WARNING " + message);
            }
        };
        try {
            new MigrationRunner(migrator, source, new RoqScaffolder(repo), builder(),
                    new MigrationOptions(output, build, console))
                    .run();
        } catch (MigrationException e) {
            System.err.println("Migration failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static SiteMigrator migratorFor(Path source) throws MigrationException {
        SiteType type = SiteType.detect(source).orElse(null);
        // A Gatsby project in a subdirectory, with its content next to it, is migrated from the parent
        if (type == null && Files.isDirectory(source.resolve("site"))) {
            type = SiteType.detect(source.resolve("site")).orElse(null);
        }
        if (type == SiteType.GATSBY) {
            return new GatsbyMigrator();
        }
        throw new MigrationException("No migrator for " + (type == null ? "this site" : type.displayName()) + " yet");
    }

    private static SiteBuilder builder() {
        return site -> {
            ProcessBuilder mvn = new ProcessBuilder("mvn", "-B", "-q", "package", "quarkus:run", "-DskipTests")
                    .directory(site.dir().toFile())
                    .inheritIO();
            mvn.environment().keySet().removeIf(name -> name.startsWith("MAVEN_"));
            mvn.environment().put("QUARKUS_ROQ_GENERATOR_BATCH", "true");
            int exit = mvn.start().waitFor();
            if (exit != 0) {
                site.report().warn("The site did not build (mvn exited with " + exit + ")");
            } else {
                site.report().info("Generated the site in " + site.dir().resolve("target/roq"));
            }
        };
    }

    private static Path findRepositoryRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("roq-cli"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Run MigrateDev from a checkout of quarkus-roq");
        }
        return dir;
    }
}
