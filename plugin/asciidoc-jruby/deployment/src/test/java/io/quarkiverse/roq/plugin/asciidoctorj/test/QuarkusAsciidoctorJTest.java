package io.quarkiverse.roq.plugin.asciidoctorj.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJConfig;
import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJConverter;
import io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJSectionHelperFactory;
import io.quarkus.qute.Engine;

public class QuarkusAsciidoctorJTest {

    public static final AsciidoctorJSectionHelperFactory FACTORY = new AsciidoctorJSectionHelperFactory(
            new AsciidoctorJConverter(new AsciidoctorJConfig() {
                @Override
                public Optional<String> icons() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> sourceHighlighter() {
                    return Optional.empty();
                }

                @Override
                public String templatesDir() {
                    return "src/main/asciidoc-templates";
                }

                @Override
                public Optional<String> outputImageDir() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> imageDir() {
                    return Optional.empty();
                }

            }));

    @Test
    public void shouldConvertUsingAsciiTag() {
        Engine engine = Engine.builder().addDefaults()
                .addSectionHelper(FACTORY).build();

        String result = engine.parse("{#ascii}...{/ascii}").render();

        assertThat(result).containsIgnoringWhitespaces("""
                 <div class="paragraph">
                 <p>&#8230;&#8203;</p>
                 </div>
                """);
    }

    @Test
    public void shouldConvertUsingAsciidocTag() {
        Engine engine = Engine.builder().addDefaults()
                .addSectionHelper(FACTORY).build();

        String result = engine.parse("{#asciidoc}...{/asciidoc}").render();

        assertThat(result).containsIgnoringWhitespaces("""
                 <div class="paragraph">
                 <p>&#8230;&#8203;</p>
                 </div>
                """);
    }

    @Test
    public void testH1() {
        Engine engine = Engine.builder().addDefaults()
                .addSectionHelper(FACTORY).build();

        String result = engine.parse("{#ascii}= Quarkus and Roq{/ascii}").render();

        assertThat(result).contains("<h1>Quarkus and Roq</h1>");
    }

    @Test
    void shouldConvertWithForTagInsideAsciiTag() {

        Engine engine = Engine.builder().addDefaults()
                .addSectionHelper(FACTORY).build();

        String result = engine.parse("""
                <h1>Quarkus and Qute</h1>
                {#ascii}
                == Qute and Roq
                Here is a list:

                {#for item in items}
                * an {item} as a list item
                {/for}
                {/ascii}
                """).data("items", List.of("apple", "banana", "cherry"))
                .render();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result).contains("<h1>Quarkus and Qute</h1>");
            softly.assertThat(result).contains("<h2 id=\"_qute_and_roq\">Qute and Roq</h2>");
            softly.assertThat(result).contains("<ul>");
            softly.assertThat(result).contains("<li>");
        });
    }
}
