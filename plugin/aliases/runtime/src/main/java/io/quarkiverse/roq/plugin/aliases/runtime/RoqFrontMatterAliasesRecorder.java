package io.quarkiverse.roq.plugin.aliases.runtime;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class RoqFrontMatterAliasesRecorder {

    public Handler<RoutingContext> addRedirect(String url) {
        return ctx -> ctx.redirect(url);
    }
}
