package io.quarkiverse.roq.plugin.aliases.runtime;

import java.util.Map;

import io.quarkus.qute.Qute;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class RoqFrontMatterAliasesRecorder {

    public Handler<RoutingContext> sendRedirectPage(String url) {
        return ctx -> {
            // language=html
            String html = Qute.fmt("""
                            <!DOCTYPE html>
                            <html lang="en-US">
                              <head>
                                  <meta charset="utf-8">
                                  <title>Redirecting&hellip;</title>
                                  <link rel="canonical" href="{url}">
                                  <meta http-equiv="refresh" content="0; url="{url}">
                                  <meta name="robots" content="noindex">
                              </head>
                              <body>
                                  <h1>Redirecting&hellip;</h1>
                                  <a href="{url}">Click here if you are not redirected.</a>
                                  <script>location="{url}"</script>
                              </body>
                            </html>
                    """, Map.of("url", url));

            ctx.response()
                    .putHeader("content-type", "text/html")
                    .end(html);
        };
    }
}
