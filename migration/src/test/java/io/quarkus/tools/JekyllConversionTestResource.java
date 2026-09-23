package io.quarkus.tools;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Copies the Jekyll test fixture to a working directory under {@code target/},
 * runs {@code roq-it-jekyll} to convert it to a Roq site, and returns
 * {@code quarkus.roq.dir} pointing at the result. This ensures the E2E test
 * serves exactly what the migration script produces — no hand-curated fixtures.
 */
public class JekyllConversionTestResource implements QuarkusTestResourceLifecycleManager {

    static final String FIXTURE_RESOURCE_ROOT = "/jekyll-site";
    static final Path WORK_DIR = Path.of("target/converted-jekyll-site");

    @Override
    public Map<String, String> start() {
        try {
            Path workDir = WORK_DIR;
            if (Files.exists(workDir)) {
                deleteRecursively(workDir);
            }

            copyFixture(workDir);
            runMigrationScript(workDir);

            return Map.of("quarkus.roq.dir", workDir.toAbsolutePath().toString());
        } catch (Exception e) {
            throw new RuntimeException("Jekyll conversion failed", e);
        }
    }

    @Override
    public void stop() {
    }

    /**
     * Copies all files from the {@code /jekyll-site} classpath resource tree into {@code target}.
     * The directory is walked at copy time so the list never goes stale when fixture files are added.
     */
    static void copyFixture(Path target) throws IOException, URISyntaxException {
        URL root = JekyllConversionTestResource.class.getResource(FIXTURE_RESOURCE_ROOT);
        if (root == null) {
            throw new IllegalStateException("Fixture resource not found: " + FIXTURE_RESOURCE_ROOT);
        }
        Path fixtureRoot = Path.of(root.toURI());
        Files.walkFileTree(fixtureRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(fixtureRoot.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = fixtureRoot.relativize(file);
                Files.copy(file, target.resolve(relative.toString()));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Runs the migration script and throws if it fails. Used by the lifecycle manager.
     */
    static void runMigrationScript(Path siteDir) throws Exception {
        int exitCode = runMigrationScriptForExitCode(siteDir);
        if (exitCode != 0) {
            throw new RuntimeException("roq-it-jekyll exited with code " + exitCode);
        }
    }

    /**
     * Runs the migration script and returns the exit code. Used by unit tests that want to assert on it.
     */
    static int runMigrationScriptForExitCode(Path siteDir) throws Exception {
        Path scriptPath = findScript();

        String bashCommand = BashCommandHelper.getBashCommand();
        String scriptPathStr = BashCommandHelper.toUnixPath(scriptPath.toAbsolutePath().toString());
        String siteDirStr = BashCommandHelper.toUnixPath(siteDir.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(bashCommand, scriptPathStr, siteDirStr);
        pb.environment().put("BATCH_MODE", "true");
        pb.redirectErrorStream(true);
        pb.directory(siteDir.toFile());

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(10, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            System.err.println("SCRIPT TIMED OUT. Output:\n" + output);
            return -1;
        }
        if (process.exitValue() != 0) {
            System.err.println("SCRIPT FAILED (exit " + process.exitValue() + "). Output:\n" + output);
        }
        return process.exitValue();
    }

    static Path findScript() {
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

    static void deleteRecursively(Path dir) throws IOException {
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
