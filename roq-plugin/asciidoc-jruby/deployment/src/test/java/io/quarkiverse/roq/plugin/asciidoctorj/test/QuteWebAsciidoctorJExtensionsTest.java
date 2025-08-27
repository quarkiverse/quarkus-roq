package io.quarkiverse.roq.plugin.asciidoctorj.test;

import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.Engine;
import io.quarkus.test.QuarkusUnitTest;

public class QuteWebAsciidoctorJExtensionsTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest()
            .withApplicationRoot(
                    app -> app
                            .addAsResource(new StringAsset("{data.asciidocify}"), "templates/foo.html")
                            .addAsResource(new StringAsset("{data.asciidocToHtml}"), "templates/bar.html"));

    @Inject
    Engine engine;

    @Test
    void shouldUseAsciidocify() {
        String asciidoc = "= Qute and Roq";
        Assertions.assertThat(engine.getTemplate("foo").data("data", asciidoc).render()).contains("<h1>Qute and Roq</h1>");
        Assertions.assertThat(engine.getTemplate("bar").data("data", asciidoc).render()).contains("<h1>Qute and Roq</h1>");
    }
}
