package io.quarkiverse.roq.it;

import static java.nio.file.Files.exists;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.QuarkusMainTest;

@QuarkusMainTest
public class RoqGenerationTest {

    @Test
    @Launch
    public void testGenerate() {
        assertTrue(exists(Path.of("target/roq/index.html")));
        assertTrue(exists(Path.of("target/roq/some-page")));
        assertTrue(exists(Path.of("target/roq/foo.json")));
        assertTrue(exists(Path.of("target/roq/assets/vector.svg")));
        assertTrue(exists(Path.of("target/roq/static/logo.svg")));
        assertTrue(exists(Path.of("target/roq/static/logo.svg.svg")));
        assertTrue(exists(Path.of("target/roq/static/élo$ bar.txt")));
    }

}
