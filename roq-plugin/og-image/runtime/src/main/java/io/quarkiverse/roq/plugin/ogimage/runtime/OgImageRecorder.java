package io.quarkiverse.roq.plugin.ogimage.runtime;

import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgImageRegistry;
import io.quarkiverse.roq.plugin.ogimage.runtime.model.OgImageTarget;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class OgImageRecorder {

    public RuntimeValue<OgImageRegistry> createRegistry(java.util.Map<String, OgImageTarget> targetsByPngPath) {
        return new RuntimeValue<>(new OgImageRegistry(targetsByPngPath));
    }

    public Handler<RoutingContext> pngHandler(OgImageTarget target) {
        return ctx -> {
            OgImageService ogImageService = Arc.container().select(OgImageService.class).get();
            byte[] png = ogImageService.renderTarget(target);
            ctx.response()
                    .putHeader("Content-Type", "image/png")
                    .putHeader("Cache-Control", "public, max-age=31536000, immutable")
                    .end(Buffer.buffer(png));
        };
    }
}
