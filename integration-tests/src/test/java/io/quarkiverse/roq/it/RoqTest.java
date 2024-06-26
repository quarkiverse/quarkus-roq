package io.quarkiverse.roq.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class RoqTest {

    @Test
    public void testIndex() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("All the tools to generate static websites out of your Quarkus web application."))
                .body(containsString("10 Tips To Improve Your Workflow"))
                .body(containsString("Conference on Java"))
                .body(containsString("4 minute read"))
                .body(containsString("2024, Jun 12"))
                .body(containsString("2024 &copy; ROQ"));
    }

    @Test
    public void testPosts() {
        given()
                .when().get("/posts/2024-08-10-tips-to-improve-your-workflow")
                .then()
                .statusCode(200)
                .body(containsString("All the tools to generate static websites out of your Quarkus web application."))
                .body(containsString("10 Tips To Improve Your Workflow"))
                .body(containsString("<h3>Literally pickled twee man braid</h3>"))
                .body(containsString("2024 &copy; ROQ"));
    }

    @Test
    public void testPage() {
        given()
                .when().get("/events")
                .then()
                .statusCode(200)
                .body(containsString("All the tools to generate static websites out of your Quarkus web application."))
                .body(containsString("<h2 class=\"event-title\">Tech Conference 2024</h2>"))
                .body(containsString("<h2 class=\"event-title\">Culinary Arts Festival</h2>"))
                .body(containsString("2024 &copy; ROQ"));
    }
}
