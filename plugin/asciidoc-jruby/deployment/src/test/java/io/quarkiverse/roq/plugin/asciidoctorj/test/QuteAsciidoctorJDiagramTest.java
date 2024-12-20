package io.quarkiverse.roq.plugin.asciidoctorj.test;

import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.qute.Engine;
import io.quarkus.test.QuarkusUnitTest;

public class QuteAsciidoctorJDiagramTest {

    @RegisterExtension
    static final QuarkusUnitTest quarkusApp = new QuarkusUnitTest();

    @Inject
    Engine engine;

    @Test
    void shouldRenderDiagram() {

        String result = engine.parse("""
                <h1>Quarkus and Qute</h1>
                {#ascii}
                == Qute and Diagram

                Here is a diagram:

                [plantuml,target="wunderbar",format=svg]
                ----
                @startuml
                Test -> Test2
                @enduml
                ----

                {/ascii}
                """).render();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result).contains("<h1>Quarkus and Qute</h1>");
            softly.assertThat(result).contains("<h2 id=\"_qute_and_diagram\">Qute and Diagram</h2>");
            softly.assertThat(result).containsPattern("<img src=.*");
            softly.assertThat(result).doesNotContain("@startuml");
        });
    }
}