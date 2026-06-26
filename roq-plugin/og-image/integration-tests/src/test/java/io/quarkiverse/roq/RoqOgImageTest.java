package io.quarkiverse.roq;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqOgImageTest {

    @Test
    void homepageHasOgImageMeta() {
        RestAssured.when().get("/")
                .then().statusCode(200)
                .body(containsString("property=\"og:image\""))
                .body(containsString("/og/index.png"))
                .body(containsString("summary_large_image"));
    }

    @Test
    void homepagePngIsValid() {
        byte[] png = RestAssured.when().get("/og/index.png")
                .then().statusCode(200)
                .contentType("image/png")
                .extract().asByteArray();

        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 'P', png[1]);
        assertEquals((byte) 'N', png[2]);
        assertEquals((byte) 'G', png[3]);
    }

    @Test
    void collectionPostPng() {
        RestAssured.when().get("/posts/og-demo-post/")
                .then().statusCode(200)
                .body(containsString("property=\"og:image\""))
                .body(containsString("/og/posts/demo.png"));

        RestAssured.when().get("/og/posts/demo.png")
                .then().statusCode(200)
                .contentType("image/png");
    }
}
