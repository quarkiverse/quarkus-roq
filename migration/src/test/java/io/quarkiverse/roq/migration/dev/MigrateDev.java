package io.quarkiverse.roq.migration.dev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.roq.migration.SiteFiles;
import io.quarkiverse.roq.migration.engine.MigrationException;
import io.quarkiverse.roq.migration.engine.MigrationOptions;
import io.quarkiverse.roq.migration.engine.MigrationOutput;
import io.quarkiverse.roq.migration.engine.MigrationRunner;
import io.quarkiverse.roq.migration.engine.Scaffolder;
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
 * The Roq project is created with the {@code roq create} of a locally built Roq CLI
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
            new MigrationRunner(migrator, source, scaffolder(repo), builder(), new MigrationOptions(output, build, console))
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

    /**
     * Runs {@code roq create} into a temporary directory and keeps its pom.xml, as roq-it-jekyll does.
     */
    private static Scaffolder scaffolder(Path repo) {
        return site -> {
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
                Files.writeString(site.dir().resolve("pom.xml"), withSnapshotProperties(pom, repo));
                site.report().info("Created pom.xml for " + name);
            } finally {
                SiteFiles.deleteTree(tmp);
            }
        };
    }

    /**
     * Roq's 999-SNAPSHOT poms use version properties that only resolve through this repository's parent pom.
     */
    private static String withSnapshotProperties(String pom, Path repo) throws IOException {
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

    private static void run(Path dir, String... command) throws IOException, InterruptedException, MigrationException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile()).inheritIO();
        // roq create reads the Maven command line from the environment; don't hand it the one running us
        builder.environment().keySet().removeIf(name -> name.startsWith("MAVEN_"));
        Process process = builder.start();
        if (process.waitFor() != 0) {
            throw new MigrationException(String.join(" ", command) + " failed");
        }
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
