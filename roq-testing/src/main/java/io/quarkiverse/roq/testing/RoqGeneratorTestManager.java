package io.quarkiverse.roq.testing;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quarkiverse.roq.generator.runtime.RoqGenerator;
import io.quarkus.arc.Arc;
import io.quarkus.test.common.QuarkusTestResourceConfigurableLifecycleManager;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;

public class RoqGeneratorTestManager implements QuarkusTestResourceConfigurableLifecycleManager<RoqAndRoll> {
    private StaticHandler staticHandler;
    private RoqAndRoll options;
    private HttpServer httpServer;
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public void init(RoqAndRoll annotation) {
        this.options = annotation;
    }

    @Override
    public void init(Map<String, String> initArgs) {
        throw new IllegalStateException("Use @RoqGeneratorTest() annotation instead");
    }

    @Override
    public Map<String, String> start() {

        return Map.of();
    }

    @Override
    public void inject(TestInjector testInjector) {
        if (started.compareAndSet(false, true)) {
            final RoqGenerator roqGenerator = Arc.container().instance(RoqGenerator.class).get();
            final Vertx vertx = Arc.container().instance(Vertx.class).get();
            final Path outputDir = roqGenerator.generateBlocking();

            staticHandler = StaticHandler.create(FileSystemAccess.ROOT, outputDir.toAbsolutePath().toString());
            Router router = Router.router(vertx);
            router.route("/*").handler(staticHandler);
            // Start the HTTP server
            httpServer = vertx.createHttpServer(new HttpServerOptions().setPort(options.port()));
            httpServer.requestHandler(router).listen(result -> {
                if (result.failed()) {
                    throw new RuntimeException("Failed to start Roq test static server", result.cause());
                }
            });
        }
        testInjector.injectIntoFields(new RoqServer(options.port()), new TestInjector.MatchesType(RoqServer.class));
    }

    @Override
    public void stop() {
        // Stop the HTTP server
        if (httpServer != null) {
            httpServer.close(result -> {
                if (result.failed()) {
                    System.err.println("Failed to stop Roq test static server: " + result.cause().getMessage());
                }
            });
        }
    }
}
