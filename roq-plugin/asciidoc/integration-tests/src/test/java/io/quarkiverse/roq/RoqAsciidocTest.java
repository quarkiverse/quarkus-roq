package io.quarkiverse.roq;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqAsciidocTest {

    @Test
    public void test1() {
        RestAssured.when().get("/guides/my-doc/").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    public void test2() {
        RestAssured.when().get("/guides/another-doc/").then().statusCode(200).log().ifValidationFails();
    }

}
