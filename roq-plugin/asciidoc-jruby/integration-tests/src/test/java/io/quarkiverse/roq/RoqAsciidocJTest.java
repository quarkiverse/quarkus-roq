package io.quarkiverse.roq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class RoqAsciidocJTest {

    @Test
    public void testMyDoc() {
        final String body = RestAssured.when().get("/guides/my-doc").then().statusCode(200).log().ifValidationFails().extract()
                .asString();
        assertThat(body).contains("<h1>Getting Started to Quarkus Messaging with AMQP 1.0</h1>");
        assertThat(body).contains("<a href=\"../rabbitmq/\">Quarkus Messaging RabbitMQ extension</a>");
        assertThat(body).contains("Roughly 15 minutes");
        assertThat(body).contains("<h3 id=\"foo\">Foo</h3>");
        assertThat(body).contains("<h4 id=\"what-is-lorem-ipsum\">What is Lorem Ipsum?</h4>");
        assertThat(body).containsIgnoringWhitespaces("""
                <div class="sect1">
                <h2 id="tags">Tags:</h2>
                <div class="sectionbody">
                <div class="paragraph">
                <p>snippet a</p>
                </div>
                <div class="paragraph">
                <p>snippet b</p>
                </div>
                <div class="paragraph">
                <p>snippet a</p>
                </div>
                </div>
                </div>
                """);
        assertThat(body)
                .containsIgnoringWhitespaces("<img src=\"/guides/images/iamroq.png\" alt=\"Architecture\" width=\"80%\">");
    }

    @Test
    public void test2() {
        RestAssured.when().get("/guides/another-doc/").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    public void testError() {
        RestAssured.when().get("/guides/include-error/").then().statusCode(200).log().ifValidationFails();
    }

    @Test
    public void testErrorAboveSite() {
        RestAssured.when().get("/guides/include-error-above-site/").then().statusCode(500).log().ifValidationFails();
    }
}
