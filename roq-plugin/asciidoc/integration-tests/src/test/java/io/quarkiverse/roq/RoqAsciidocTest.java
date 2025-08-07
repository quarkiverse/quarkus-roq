package io.quarkiverse.roq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqAsciidocTest {

    @Test
    public void testMyDoc() {
        final String body = RestAssured.when().get("/guides/my-doc").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        assertThat(body).contains("<h1>Getting Started to Quarkus Messaging with AMQP 1.0</h1>");
        assertThat(body).contains("<a href=\"../rabbitmq/\">Quarkus Messaging RabbitMQ extension</a>");
        assertThat(body).contains("Roughly 15 minutes");
        assertThat(body).contains("<h3>Foo</h3>");
        assertThat(body).contains("<h4>What is Lorem Ipsum?</h4>");
        assertThat(body)
                .containsIgnoringWhitespaces("<img src=\"/guides/images/iamroq.png\" alt=\"Architecture\" width=\"80%\">");
    }

    @Test
    public void test2() {
        RestAssured.when().get("/guides/another-doc/").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    public void testError() {
        RestAssured.when().get("/guides/include-error/").then().statusCode(500).log().ifValidationFails();
    }

}
