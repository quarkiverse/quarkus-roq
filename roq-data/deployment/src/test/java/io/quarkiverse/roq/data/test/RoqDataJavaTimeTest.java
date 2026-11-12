package io.quarkiverse.roq.data.test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/** A @DataMapping record can bind YAML dates straight to java.time types. */
public class RoqDataJavaTimeTest {

    @RegisterExtension
    static final QuarkusExtensionTest quarkusUnitTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(Release.class, ReleaseResource.class)
                    .add(new StringAsset("""
                            quarkus.roq.dir=src/test/roq-dates
                            quarkus.roq.jackson.property-naming-strategy=SNAKE_CASE
                            """), "application.properties"));

    @Test
    public void bindsJavaTimeTypes() {
        RestAssured.given()
                .get("/release")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("3.20 2025-03-26 2026-03-28T10:15:30 2025-03-27"));
    }

    @DataMapping("release")
    public record Release(String version, LocalDate releaseDate, LocalDateTime eol) {
    }

    @ApplicationScoped
    @Path("/release")
    public static class ReleaseResource {

        @Inject
        Release release;

        @GET
        public String get() {
            // a real LocalDate, not a string: date arithmetic works
            return release.version() + " " + release.releaseDate() + " " + release.eol() + " "
                    + release.releaseDate().plusDays(1);
        }
    }
}
