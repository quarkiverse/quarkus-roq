package io.quarkiverse.statiq.it;

import static io.restassured.RestAssured.given;
import static java.nio.file.Files.exists;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class StatiqResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/statiq?name=statiq")
                .then()
                .statusCode(200)
                .body(is("Hello statiq"));
    }

    @Test
    public void testGenerate() {
        given()
                .baseUri("http://localhost:9000")
                .when().get("/q/statiq/generate")
                .then()
                .statusCode(200)
                .body(startsWith("Generated in:"))
                .body(endsWith("/target/statiq"));

        assertTrue(exists(Path.of("target/statiq/index.html")));
        assertTrue(exists(Path.of("target/statiq/some-page")));
        assertTrue(exists(Path.of("target/statiq/statiq-name-bar")));
        assertTrue(exists(Path.of("target/statiq/statiq-name-foo")));
        assertTrue(exists(Path.of("target/statiq/assets/vector.svg")));

        // FIXME: this will work with next web-bundler release
        //assertTrue(exists(Path.of("target/statiq/static/logo.svg")));
    }
}
