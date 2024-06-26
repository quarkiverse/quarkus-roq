package io.quarkiverse.roq.data.test;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

public class RoqDataDevModeTest {

    // Start hot reload (DevMode) test with your extension loaded
    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .withApplicationRoot((jar) -> jar
                    .addClass(Hello.class)
                    .addAsResource("foo.json", "site/_data/foo.json"));

    @Test
    public void changeData() {
        RestAssured.given()
                .get("/hello")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("Super Heroes from Json"));
        devModeTest.modifyResourceFile("site/_data/foo.json", (content) -> content.replace("Super", "Mega"));
        RestAssured.given()
                .get("/hello")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("Mega Heroes from Json"));
    }

    @ApplicationScoped
    @Path("/hello")
    public static class Hello {

        @Inject
        @Named("foo")
        JsonObject foo;

        @GET
        public String foo() {
            return foo.getString("name");
        }
    }
}
