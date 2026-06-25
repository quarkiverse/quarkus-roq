package io.quarkus.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Copies the Jekyll test fixture to a working directory under {@code target/},
 * runs {@code roq-it-jekyll} to convert it to a Roq site, and returns
 * {@code quarkus.roq.dir} pointing at the result. This ensures the E2E test
 * serves exactly what the migration script produces — no hand-curated fixtures.
 */
public class JekyllConversionTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String[] FIXTURE_FILES = {
            "_config.yml",
            "_includes/header.html",
            "_layouts/default.html",
            "_layouts/home.html",
            "_layouts/page.html",
            "_layouts/post.html",
            "_posts/2024-01-15-hello-world.md",
            "_site/index.html",
            "_site/assets/css/main.css",
            "assets/css/main.css",
            "Gemfile",
            "Gemfile.lock",
            "index.md",
            "about.md"
    };

    private Path workDir;

    @Override
    public Map<String, String> start() {
        try {
            workDir = Path.of("target/converted-jekyll-site");
            if (Files.exists(workDir)) {
                deleteRecursively(workDir);
            }

            copyFixture(workDir);
            runMigrationScript(workDir);

            Path absWorkDir = workDir.toAbsolutePath();
            System.err.println("[TEST-RESOURCE] CWD = " + Path.of("").toAbsolutePath());
            System.err.println("[TEST-RESOURCE] workDir = " + absWorkDir);
            System.err.println("[TEST-RESOURCE] workDir exists = " + Files.exists(absWorkDir));
            System.err.println("[TEST-RESOURCE] content dir exists = " + Files.exists(absWorkDir.resolve("content")));
            System.err.println("[TEST-RESOURCE] templates dir exists = " + Files.exists(absWorkDir.resolve("templates")));
            if (Files.exists(absWorkDir.resolve("content"))) {
                try (var walk = Files.walk(absWorkDir.resolve("content"), 3)) {
                    walk.forEach(p -> System.err.println("[TEST-RESOURCE] content: " + absWorkDir.relativize(p)));
                }
            }

            return Map.of("quarkus.roq.dir", absWorkDir.toString());
        } catch (Exception e) {
            throw new RuntimeException("Jekyll conversion failed", e);
        }
    }

    @Override
    public void stop() {
    }

    private void copyFixture(Path target) throws IOException {
        for (String file : FIXTURE_FILES) {
            Path dest = target.resolve(file);
            Files.createDirectories(dest.getParent());
            try (InputStream is = Objects.requireNonNull(
                    getClass().getResourceAsStream("/jekyll-site/" + file),
                    "Missing fixture: /jekyll-site/" + file)) {
                Files.copy(is, dest);
            }
        }
        Files.createDirectories(target.resolve("_data"));
    }

    private void runMigrationScript(Path siteDir) throws Exception {
        Path scriptPath = findScript();

        ProcessBuilder pb = new ProcessBuilder("bash", scriptPath.toString(), siteDir.toAbsolutePath().toString());
        pb.environment().put("BATCH_MODE", "true");
        pb.inheritIO();
        pb.directory(siteDir.toFile());

        Process process = pb.start();
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("roq-it-jekyll timed out after 5 minutes");
        }
        if (process.exitValue() != 0) {
            throw new RuntimeException("roq-it-jekyll exited with code " + process.exitValue());
        }
    }

    private Path findScript() {
        Path script = Path.of("roq-it-jekyll");
        if (Files.exists(script)) {
            return script.toAbsolutePath();
        }
        script = Path.of("migration/roq-it-jekyll");
        if (Files.exists(script)) {
            return script.toAbsolutePath();
        }
        throw new RuntimeException("Cannot find roq-it-jekyll script");
    }

    private void deleteRecursively(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (exc != null)
                    throw exc;
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
