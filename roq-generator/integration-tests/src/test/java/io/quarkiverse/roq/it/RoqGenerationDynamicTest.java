package io.quarkiverse.roq.it;

import static java.nio.file.Files.exists;
import static java.nio.file.Files.readString;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.generator.runtime.RoqSelection;
import io.quarkiverse.roq.generator.runtime.SelectedPath;
import io.quarkus.runtime.configuration.ConfigUtils;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.QuarkusMainTest;

/**
 * Integration test demonstrating dynamic selection with a data source.
 * This test validates the documentation example showing how to generate pages
 * from a list of posts retrieved from an API or data source at build time.
 */
@QuarkusMainTest
@TestProfile(RoqGenerationDynamicTest.DynamicConfig.class)
public class RoqGenerationDynamicTest {

    @Test
    @Launch(value = {}, exitCode = 0)
    public void testDynamicSelection() throws Exception {
        // Verify pages were generated from dynamic selection
        assertTrue(exists(Path.of("target/roq/api/blog/posts/hello-world/index.html")));
        assertTrue(exists(Path.of("target/roq/api/blog/posts/roq-intro/index.html")));

        // Verify content
        String content = readString(Path.of("target/roq/api/blog/posts/hello-world/index.html"));
        assertTrue(content.contains("hello-world"));
    }

    public static class DynamicConfig implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "dynamic";
        }
    }

    /**
     * This simulates the documentation example where posts are fetched from an external source.
     * In a real application, this would inject a REST client or database EntityManager.
     */
    @ApplicationScoped
    public static class DynamicSelection {
        @Inject
        BlogApiResource blogApi; // Using local resource instead of REST client for testing

        @Produces
        @Singleton
        RoqSelection produce() {
            if (ConfigUtils.isProfileActive("dynamic")) {
                // Fetch posts from "API" (local resource in this test)
                List<BlogPost> posts = blogApi.getAllPosts();

                // Generate a page for each post
                List<SelectedPath> paths = posts.stream()
                        .map(post -> SelectedPath.builder()
                                .html("/api/blog/posts/" + post.slug())
                                .build())
                        .toList();

                return new RoqSelection(paths);
            }
            return new RoqSelection(List.of());
        }
    }
}
