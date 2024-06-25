package io.quarkiverse.roq.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.runtime.util.PathUtils;
import io.quarkus.arc.All;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.HttpConfiguration;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

@ApplicationScoped
public class RoqGenerator implements Handler<RoutingContext> {

    private static final Logger LOGGER = Logger.getLogger(io.quarkiverse.roq.runtime.RoqGenerator.class);
    private final Instance<Vertx> vertx;
    private final RoqConfig config;

    private final HttpConfiguration httpConfiguration;

    private final HttpBuildTimeConfig httpBuildTimeConfig;
    private WebClient client;
    private final List<StaticPage> staticPages;

    @Inject
    public RoqGenerator(final Instance<Vertx> vertx, final RoqConfig config, HttpConfiguration httpConfiguration,
            HttpBuildTimeConfig httpBuildTimeConfig,
            @All final List<StaticPages> staticPages) {
        this.vertx = vertx;
        this.config = config;
        this.httpConfiguration = httpConfiguration;
        this.httpBuildTimeConfig = httpBuildTimeConfig;
        this.staticPages = staticPages.stream().map(StaticPages::pages).flatMap(List::stream)
                .sorted(Comparator.comparing(StaticPage::outputPath)).toList();
    }

    void onStart(@Observes StartupEvent ev) {
        if (config.batch()) {
            generate().subscribe().with(t -> {
                LOGGER.info("Roq generation succeeded in directory: " + outputDir());
                Quarkus.asyncExit();
            }, message -> {
                Log.error(message);
                Quarkus.asyncExit();
            });
        }
    }

    public String outputDir() {
        return PathUtils.join(FixedStaticPagesProvider.targetDir(), config.outputDir());
    }

    private WebClient client() {
        if (client == null) {
            client = WebClient.create(vertx.get());
        }
        return client;
    }

    @Override
    public void handle(RoutingContext event) {
        generate().subscribe().with(t -> {
            event.response().setStatusCode(200);
            event.response().end("Generated in: " + outputDir());
        }, event::fail);
    }

    public Uni<List<Void>> generate() {
        final FileSystem fs = vertx.get().fileSystem();
        final List<Uni<Void>> all = new ArrayList<>();
        final Path outputDir = Path.of(outputDir()).toAbsolutePath();
        for (StaticPage page : this.staticPages) {
            all.add(Uni.createFrom().completionStage(() -> fetchContent(page.path()))
                    .chain(r -> {
                        final Path targetPath = outputDir.resolve(page.outputPath());
                        return Uni.createFrom()
                                .completionStage(() -> fs.mkdirs(targetPath.getParent().toString()).toCompletionStage())
                                .chain(() -> {
                                    LOGGER.debugf("Roq is writing %s", targetPath.toString());
                                    return Uni.createFrom().completionStage(fs
                                            .writeFile(targetPath.toString(), r.bodyAsBuffer()).toCompletionStage());
                                });
                    }));

        }

        return Uni.createFrom().completionStage(() -> fs.exists(outputDir.toString()).compose(r -> {
            if (r) {
                return fs.deleteRecursive(outputDir.toString(), true);
            } else {
                return Future.succeededFuture();
            }
        }).toCompletionStage())
                .chain(() -> Uni.join().all(all).andFailFast())
                .ifNoItem().after(Duration.ofSeconds(config.timeout()))
                .fail();
    }

    private CompletionStage<HttpResponse<Buffer>> fetchContent(String path) {
        LOGGER.debugf("Roq is fetching %s", path);
        final String host;
        final int port;
        if (LaunchMode.current() == LaunchMode.TEST) {
            host = httpConfiguration.testHost.orElse(httpConfiguration.host);
            port = httpConfiguration.testPort;
        } else {
            host = httpConfiguration.host;
            port = httpConfiguration.port;
        }
        return client().get(port, host, PathUtils.join(httpBuildTimeConfig.rootPath, path))
                .send().toCompletionStage();
    }

}
