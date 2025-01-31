package io.quarkiverse.roq.testing;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quarkiverse.roq.generator.runtime.RoqGenerator;
import io.quarkus.arc.Arc;
import io.quarkus.test.common.RestAssuredURLManager;
import io.quarkus.test.junit.callback.*;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;

public class RoqGeneratorTestCallback
        implements QuarkusTestAfterConstructCallback, QuarkusTestAfterAllCallback, QuarkusTestBeforeTestExecutionCallback {
    private static final RoqAndRollManager INSTANCE = new RoqAndRollManager();

    @Override
    public void afterConstruct(Object testInstance) {
        INSTANCE.afterConstruct(testInstance);
    }

    @Override
    public void beforeTestExecution(QuarkusTestMethodContext context) {
        INSTANCE.beforeTestExecution(context);
    }

    @Override
    public void afterAll(QuarkusTestContext context) {
        INSTANCE.afterAll(context);
    }

    private static class RoqAndRollManager
            implements QuarkusTestAfterConstructCallback, QuarkusTestAfterAllCallback, QuarkusTestBeforeTestExecutionCallback {
        private StaticHandler staticHandler;
        private RoqAndRoll options;
        private HttpServer httpServer;
        private final AtomicBoolean started = new AtomicBoolean(false);
        private static final RoqGeneratorTestCallback INSTANCE = new RoqGeneratorTestCallback();

        private RoqAndRollManager() {
        }

        @Override
        public void afterConstruct(Object testInstance) {
            final RoqAndRoll annotation = testInstance.getClass().getAnnotation(RoqAndRoll.class);
            if (annotation != null) {
                this.options = annotation;
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
            }

        }

        @Override
        public void beforeTestExecution(QuarkusTestMethodContext context) {
            if (this.options != null) {
                RestAssuredURLManager.setURL(false, this.options.port());
            }
        }

        @Override
        public void afterAll(QuarkusTestContext context) {
            // Stop the HTTP server
            if (httpServer != null) {
                this.options = null;
                this.started.set(false);
                httpServer.close(result -> {
                    if (result.failed()) {
                        System.err.println("Failed to stop Roq test static server: " + result.cause().getMessage());
                    }
                });
            }
        }

    }

}
