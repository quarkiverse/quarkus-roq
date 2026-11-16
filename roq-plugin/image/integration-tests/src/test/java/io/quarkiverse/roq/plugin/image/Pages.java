package io.quarkiverse.roq.plugin.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import io.restassured.RestAssured;

/** Rendered pages of the integration site. */
final class Pages {

    private Pages() {
    }

    static String get(String path) {
        return RestAssured.when().get(path).then().statusCode(200).log().ifValidationFails().extract().asString();
    }

    /** The markup from the element with the given class to the end of its section. */
    static String section(String body, String cssClass) {
        int start = body.indexOf("class=\"" + cssClass + "\"");
        assertThat(start).as("section " + cssClass).isNotNegative();
        return body.substring(start, body.indexOf("</section>", start));
    }

    /** Every generated image path in the body: attribute values, srcset entries, css urls and bare direct urls. */
    static List<String> generatedPaths(String body) {
        return Stream.of(body.split("[\"'\\s,()<]+"))
                .filter(token -> token.startsWith("/static/images/generated/"))
                .toList();
    }
}
