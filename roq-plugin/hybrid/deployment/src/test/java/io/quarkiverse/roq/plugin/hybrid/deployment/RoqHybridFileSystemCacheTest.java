package io.quarkiverse.roq.plugin.hybrid.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

public class RoqHybridFileSystemCacheTest {

    static final String BUILD_ID = "test-build";
    static final Path cacheDir = Path.of("target", "roq-hybrid-fs-test");
    static final Path buildDir = cacheDir.resolve(BUILD_ID);
    static final Path staleBuildDir = cacheDir.resolve("aabbccdd");
    static final Path userDir = cacheDir.resolve("my-custom-dir");

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = createTest();

    static QuarkusExtensionTest createTest() {
        return new QuarkusExtensionTest()
                .overrideConfigKey("quarkus.roq.resource-dir", "hybrid-test-site")
                .overrideConfigKey("site.hybrid.cache-mode", "lazy")
                .overrideConfigKey("site.hybrid.cache-store", "filesystem")
                .overrideConfigKey("site.hybrid.cache-in-dev-mode", "true")
                .overrideConfigKey("site.hybrid.cache-dir", cacheDir.toString())
                .overrideConfigKey("site.hybrid.cache-build-id", BUILD_ID)
                .setFlatClassPath(true)
                .setBeforeAllCustomizer(() -> {
                    try {
                        Files.createDirectories(staleBuildDir);
                        Files.writeString(staleBuildDir.resolve("old.html"), "stale");
                        Files.createDirectories(userDir);
                        Files.writeString(userDir.resolve("keep.txt"), "keep");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .withApplicationRoot((jar) -> jar
                        .addAsResource("hybrid-test-site"));
    }

    @Test
    void testPageRendersAndCreatesFileOnDisk() {
        RestAssured.when().get("/").then()
                .statusCode(200)
                .body(containsString("Welcome to Roq Hybrid"));

        assertThat(buildDir.resolve("index.html")).exists();
    }

    @Test
    void testCachedFileContainsRenderedHtml() throws Exception {
        RestAssured.when().get("/").then().statusCode(200);

        assertThat(Files.readString(buildDir.resolve("index.html")))
                .contains("Welcome to Roq Hybrid");
    }

    @Test
    void testNoCachePageDoesNotCreateFile() {
        RestAssured.when().get("/pages/no-cache/").then()
                .statusCode(200)
                .body(containsString("No Cache Page"));

        assertThat(buildDir.resolve("pages/no-cache.html")).doesNotExist();
    }

    @Test
    void testLastModifiedHeader() {
        RestAssured.when().get("/").then()
                .statusCode(200)
                .header("Last-Modified", notNullValue());
    }

    @Test
    void testStaleBuildDirCleaned() {
        assertThat(staleBuildDir).doesNotExist();
    }

    @Test
    void testUserDirPreserved() {
        assertThat(userDir).exists();
        assertThat(userDir.resolve("keep.txt")).exists();
    }

    @Test
    void test304NotModified() {
        String lastModified = RestAssured.when().get("/").then()
                .statusCode(200)
                .extract().header("Last-Modified");

        RestAssured.given()
                .header("If-Modified-Since", lastModified)
                .when().get("/")
                .then()
                .statusCode(304);
    }
}
