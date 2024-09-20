package io.quarkiverse.roq.data.test;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.data.test.util.Foo;
import io.quarkiverse.roq.data.test.util.Foos;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class RoqDataBindingTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusUnitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(Foo.class, Foos.class)
                    .add(new StringAsset("quarkus.roq.dir=src/test/site"),
                            "application.properties"));

    @Test
    public void foo() {
        RestAssured.given()
                .get("/foo")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("Super Heroes from Json"));
    }

    @Test
    public void testList() {
        RestAssured.given()
                .get("/list")
                .then()
                .statusCode(200)
                .body(Matchers.containsString("Super Heroes 1 from Yaml"),
                        Matchers.containsString("Super Heroes 2 from Yaml"),
                        Matchers.containsString("Super Heroes 3 from Yaml"));
    }

    @ApplicationScoped
    @Path("/foo")
    public static class PersonResource {

        @Inject
        Foo foo;

        @GET
        public String getFoo() {
            return foo.toString();
        }
    }

    @ApplicationScoped
    @Path("/list")
    public static class ListResource {

        @Inject
        Foos foos;

        @GET
        public String hi() {
            return foos.list().toString();

        }
    }
}
