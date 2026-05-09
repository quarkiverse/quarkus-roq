package io.quarkiverse.roq.plugin.prism.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link RoqPluginPrismProcessor#assembleJs} reads the right files from the
 * {@code org.mvnpm:prismjs} JAR on the test classpath and concatenates them in the right order.
 */
class AssembleJsTest {

    private static final ClassLoader CL = Thread.currentThread().getContextClassLoader();

    @Test
    void emptyLanguagesProducesCorePlusTrigger() throws IOException {
        String base = RoqPluginPrismProcessor.resolvePrismResourceBase(CL);
        String core = readResource(base + "components/prism-core.min.js");

        String js = RoqPluginPrismProcessor.assembleJs(CL, base, List.of());

        // Order: core, then '\n', then the auto-highlight trigger
        assertThat(js).startsWith(core);
        assertThat(js).endsWith("Prism.highlightAll();}");
        // No language between core and the trigger (only the separator newline)
        assertThat(js.length()).isEqualTo(core.length() + 1 + countAutoHighlightChars());
    }

    @Test
    void singleLanguageIsAppendedAfterCore() throws IOException {
        String base = RoqPluginPrismProcessor.resolvePrismResourceBase(CL);
        String core = readResource(base + "components/prism-core.min.js");
        String bash = readResource(base + "components/prism-bash.min.js");

        String js = RoqPluginPrismProcessor.assembleJs(CL, base, List.of("bash"));

        // Core comes first, then a separator newline, then bash, then the trigger
        assertThat(js).startsWith(core + "\n" + bash);
        assertThat(js).contains(bash);
        assertThat(js).endsWith("Prism.highlightAll();}");
    }

    @Test
    void languagesAppearInTheGivenOrder() throws IOException {
        String base = RoqPluginPrismProcessor.resolvePrismResourceBase(CL);
        String clike = readResource(base + "components/prism-clike.min.js");
        String java = readResource(base + "components/prism-java.min.js");

        // 'clike' must precede 'java' (java's grammar uses extend("clike", ...))
        String js = RoqPluginPrismProcessor.assembleJs(CL, base, List.of("clike", "java"));

        int clikeIdx = js.indexOf(clike);
        int javaIdx = js.indexOf(java);
        assertThat(clikeIdx).as("clike grammar must be present").isPositive();
        assertThat(javaIdx).as("java grammar must be present").isPositive();
        assertThat(clikeIdx).as("clike must come before java").isLessThan(javaIdx);
    }

    @Test
    void languagesAreSeparatedByNewlines() throws IOException {
        String base = RoqPluginPrismProcessor.resolvePrismResourceBase(CL);
        String clike = readResource(base + "components/prism-clike.min.js");
        String java = readResource(base + "components/prism-java.min.js");

        String js = RoqPluginPrismProcessor.assembleJs(CL, base, List.of("clike", "java"));

        // The processor inserts '\n' before every appended language file
        assertThat(js).contains("\n" + clike);
        assertThat(js).contains("\n" + java);
    }

    @Test
    void unknownLanguageRaisesIoException() throws IOException {
        String base = RoqPluginPrismProcessor.resolvePrismResourceBase(CL);

        // The classpath has no prism-not-a-language.min.js — read() throws IllegalStateException
        assertThatThrownBy(() -> RoqPluginPrismProcessor.assembleJs(CL, base, List.of("not-a-language")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing classpath resource")
                .hasMessageContaining("prism-not-a-language.min.js");
    }

    @Test
    void prismResourceBasePointsAtTheVersionedDirectory() throws IOException {
        String base = RoqPluginPrismProcessor.resolvePrismResourceBase(CL);

        assertThat(base)
                .startsWith("META-INF/resources/_static/prismjs/")
                .endsWith("/");
        // The directory exists on the classpath: prism-core must resolve from it
        assertThat(CL.getResource(base + "components/prism-core.min.js")).isNotNull();
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = CL.getResourceAsStream(path)) {
            assertThat(in).as("resource on test classpath: %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Length of the AUTO_HIGHLIGHT constant in {@link RoqPluginPrismProcessor}. */
    private static int countAutoHighlightChars() {
        return ("if(document.readyState==='loading'){"
                + "document.addEventListener('DOMContentLoaded',function(){Prism.highlightAll();});"
                + "}else{Prism.highlightAll();}").length();
    }
}
