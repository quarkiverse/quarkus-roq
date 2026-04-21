package io.quarkiverse.roq.frontmatter.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqException;
import io.quarkiverse.roq.frontmatter.runtime.exception.RoqStaticFileException;

@DisplayName("Roq FrontMatter - error page renderer")
class RoqErrorPageRendererUnitTest {

    @Test
    @DisplayName("renders a user-friendly page with hidden stacktrace details")
    void rendersErrorPage() {
        String html = RoqErrorPageRenderer.render("/posts/error-file", null,
                new RoqStaticFileException(RoqException.builder("Missing <asset> & \"quotes\"")));

        assertTrue(html.contains("We couldn't render this page"));
        assertTrue(html.contains("Show technical details"));
        assertTrue(html.contains("&lt;asset&gt; &amp; &quot;quotes&quot;"));
        assertTrue(html.contains("<details>"));
        assertTrue(html.contains("RoqStaticFileException"));
    }

    @Test
    @DisplayName("finds Roq exceptions anywhere in the cause chain")
    void findsRoqCauseInChain() {
        Throwable cause = new IllegalStateException("wrapper", new RoqStaticFileException(RoqException.builder("nested")));

        assertTrue(RoqErrorPageRenderer.roqCause(cause) instanceof RoqStaticFileException);
    }

}
