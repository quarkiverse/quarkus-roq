package io.quarkiverse.statiq.it;

import static java.nio.file.Files.exists;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.QuarkusMainTest;

@QuarkusMainTest
public class StatiqResourceTest {

    @Test
    @Launch(value = {}, exitCode = 0)
    public void testGenerate() {
        assertTrue(exists(Path.of("target/statiq/index.html")));
        assertTrue(exists(Path.of("target/statiq/some-page")));
        assertTrue(exists(Path.of("target/statiq/statiq-name-foo-html/index.html")));
        assertTrue(exists(Path.of("target/statiq/statiq-name-bar")));
        assertTrue(exists(Path.of("target/statiq/statiq-name-foo")));
        assertTrue(exists(Path.of("target/statiq/assets/vector.svg")));
        assertTrue(exists(Path.of("target/statiq/static/logo.svg")));
    }
}
