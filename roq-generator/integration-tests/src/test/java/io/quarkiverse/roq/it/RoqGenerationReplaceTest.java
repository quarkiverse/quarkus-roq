package io.quarkiverse.roq.it;

import static java.nio.file.Files.exists;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Produces;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.generator.runtime.RoqSelection;
import io.quarkiverse.roq.generator.runtime.SelectedPath;
import io.quarkus.runtime.configuration.ConfigUtils;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.QuarkusMainTest;

@QuarkusMainTest
@TestProfile(RoqGenerationReplaceTest.ReplaceConfig.class)
public class RoqGenerationReplaceTest {

    @Test
    @Launch(value = {}, exitCode = 0)
    public void testGenerate() {
        assertTrue(exists(Path.of("target/roq/index.html")));
        assertTrue(exists(Path.of("target/roq/some-page")));
        assertTrue(exists(Path.of("target/roq/roq-name-foo-html/index.html")));
        assertTrue(exists(Path.of("target/roq/roq-name-bar")));
        assertTrue(exists(Path.of("target/roq/roq-name-foo")));
        assertTrue(exists(Path.of("target/roq/roq-name-foo2")));
        assertTrue(exists(Path.of("target/roq/foo.json")));
        assertTrue(exists(Path.of("target/roq/assets/vector.svg")));
        assertTrue(exists(Path.of("target/roq/static/logo.svg")));
        assertTrue(exists(Path.of("target/roq/static/logo.svg.svg")));
        assertTrue(exists(Path.of("target/roq/static/-lo--bar.txt")));
    }

    public static class ReplaceConfig implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "replace";
        }
    }

    @Produces
    @Singleton
    @Transactional
    RoqSelection produce() {
        if (ConfigUtils.isProfileActive("replace")) {
            return new RoqSelection(List.of(
                    SelectedPath.builder().html("/roq?name=foo-html").build(),
                    SelectedPath.builder().path("/roq?name=foo").build(),
                    SelectedPath.builder().path("/roq?name=bar").build()));
        }
        return new RoqSelection(List.of());
    }

}
