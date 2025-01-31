package io.quarkiverse.roq.generator.runtime;

import static io.quarkiverse.roq.generator.runtime.RoqSelection.prepare;
import static io.quarkiverse.roq.generator.runtime.StaticFile.FetchType.CLASSPATH;
import static io.quarkiverse.roq.generator.runtime.StaticFile.FetchType.FILE;
import static io.quarkiverse.roq.util.PathUtils.toUnixPath;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.arc.All;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.Quarkus;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.HttpConfiguration;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

@ApplicationScoped
public class RoqGenerator implements Handler<RoutingContext> {

    private static final Logger LOGGER = Logger.getLogger(RoqGenerator.class);
    private final Instance<Vertx> vertx;
    private final RoqGeneratorConfig config;

    private final HttpConfiguration httpConfiguration;

    private final HttpBuildTimeConfig httpBuildTimeConfig;
    private final Map<String, StaticFile> staticFiles;
    private WebClient client;
    private final List<SelectedPath> selectedPaths;

    @Inject
    public RoqGenerator(final Instance<Vertx> vertx,
            final RoqGeneratorConfig config,
            HttpConfiguration httpConfiguration,
            HttpBuildTimeConfig httpBuildTimeConfig,
            @All final List<RoqSelection> selection) {
        this.vertx = vertx;
        this.config = config;
        this.httpConfiguration = httpConfiguration;
        this.httpBuildTimeConfig = httpBuildTimeConfig;
        this.staticFiles = ConfiguredPathsProvider.staticFiles();
        selectedPaths = prepare(config, selection);
    }

    void onStart(@Observes Router router) {
        router.route("/roq/ping").method(HttpMethod.GET).handler(routingContext -> {
            routingContext.response().end("pong");
        });
        if (config.batch()) {
            generate().subscribe().with(t -> {
                LOGGER.info("Roq generation succeeded in directory: " + outputDir());
                Quarkus.asyncExit(0);
            }, throwable -> {
                if (throwable instanceof ConnectException) {
                    LOGGER.error("Roq generation failed");
                } else {
                    LOGGER.error("Roq generation failed", throwable);
                }

                Quarkus.asyncExit(1);
            });
        }
    }

    public String outputDir() {
        return toUnixPath(PathUtils.join(ConfiguredPathsProvider.targetDir(), config.outputDir()));
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
            event.response().end("Exported in: " + outputDir());
        }, event::fail);
    }

    public Path generateBlocking() {
        return generate().await().atMost(Duration.ofSeconds(10));
    }

    public Uni<Path> generate() {
        final FileSystem fs = vertx.get().fileSystem();
        final List<Uni<Void>> all = new ArrayList<>();
        final Path outputDir = Path.of(outputDir()).toAbsolutePath();
        for (SelectedPath path : this.selectedPaths) {
            all.add(fetchContent(path.path())
                    .onFailure()
                    .retry().atMost(config.requestRetry())
                    .chain(r -> {
                        final Path targetPath = outputDir.resolve(path.outputPath());
                        return Uni.createFrom()
                                .completionStage(() -> fs.mkdirs(targetPath.getParent().toString()).toCompletionStage())
                                .chain(() -> Uni.createFrom().completionStage(fs
                                        .writeFile(targetPath.toString(), r).toCompletionStage()))
                                .invoke(() -> LOGGER.infof("Roq generated file %s", path.outputPath()));
                    }));

        }

        return clearOutputDir(fs, outputDir)
                .chain(this::pollRoqPing)
                .chain(() -> Uni.join().all(all).andFailFast().map(l -> outputDir))
                .ifNoItem().after(Duration.ofSeconds(config.timeout()))
                .fail();
    }

    private static Uni<Void> clearOutputDir(FileSystem fs, Path outputDir) {
        return Uni.createFrom().completionStage(() -> fs.exists(outputDir.toString()).compose(r -> {
            if (r) {
                return fs.deleteRecursive(outputDir.toString(), true);
            } else {
                return Future.succeededFuture();
            }
        }).toCompletionStage());
    }

    private Uni<Void> pollRoqPing() {
        final String pingPath = PathUtils.join(httpBuildTimeConfig.rootPath, "/roq/ping");
        return Multi.createFrom().ticks().every(Duration.ofMillis(500))
                .onItem().transformToUniAndMerge(tick -> getSend(pingPath)
                        .map(r -> true)
                        .onFailure().recoverWithItem(false))
                .filter(success -> success)
                .toUni()
                .replaceWithVoid()
                .ifNoItem().after(Duration.ofSeconds(30))
                .failWith(() -> new RuntimeException(
                        "Quarkus didn't start after 30 seconds (no response on '%s').".formatted(pingPath)));
    }

    private Uni<Buffer> fetchContent(String path) {
        if (staticFiles.containsKey(path)) {
            final StaticFile staticFile = staticFiles.get(path);
            if (staticFile.type().equals(FILE)) {
                LOGGER.debugf("Roq is reading %s from file", path);
                return Uni.createFrom().completionStage(() -> vertx.get().fileSystem().readFile(staticFile.path())
                        .onComplete(r -> LOGGER.debugf("Roq successfully read file %s", path))
                        .toCompletionStage());
            } else if (staticFile.type().equals(CLASSPATH)) {
                LOGGER.debugf("Roq is reading %s from classpath", path);
                return Uni.createFrom().completionStage(
                        () -> vertx.get().executeBlocking(() -> getClasspathResourceContent(staticFile.path()), false)
                                .map(Buffer::buffer)
                                .onComplete(r -> LOGGER.debugf("Roq successfully read %s on classpath", path))
                                .toCompletionStage());
            }
        }

        final String fullPath = encode(PathUtils.join(httpBuildTimeConfig.rootPath, path));
        LOGGER.debugf("Roq is reading %s from http", fullPath);
        return getSend(fullPath)
                .onFailure().invoke(t -> LOGGER.errorf(t, "Roq request failed %s", fullPath))
                .invoke(r -> LOGGER.debugf("Roq request completed %s", fullPath))
                .map(HttpResponse::bodyAsBuffer);
    }

    private Uni<HttpResponse<Buffer>> getSend(String path) {
        final String host;
        final int port;
        if (LaunchMode.current() == LaunchMode.TEST) {
            host = httpConfiguration.testHost.orElse(httpConfiguration.host);
            port = httpConfiguration.testPort;
        } else {
            host = httpConfiguration.host;
            port = httpConfiguration.port;
        }
        return Uni.createFrom().completionStage(() -> client().get(port, host, path)
                .send()
                .expecting(HttpResponseExpectation.status(200))
                .toCompletionStage());
    }

    public static String encode(String p) {
        int queryIndex = p.indexOf('?');
        String path = queryIndex >= 0 ? p.substring(0, queryIndex) : p;
        String query = queryIndex >= 0 ? p.substring(queryIndex + 1) : null;

        try {
            URI uri = new URI(null, null, path, null);
            if (query != null) {
                // We assume query is already encoded when it's there
                return uri.toASCIIString() + "?" + query;
            }
            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

    }

    private byte[] getClasspathResourceContent(String resourceName) {
        URL resource = getClassLoader().getResource(resourceName);
        if (resource == null) {
            LOGGER.warnf("The resource '%s' does not exist on classpath", resourceName);
            return null;
        }
        try {
            try (InputStream inputStream = resource.openStream()) {
                return inputStream.readAllBytes();
            }
        } catch (IOException e) {
            LOGGER.error("Error while reading file from Classpath for path " + resourceName, e);
            return null;
        }
    }

    private ClassLoader getClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = getClass().getClassLoader();
        }
        if (cl == null) {
            cl = Object.class.getClassLoader();
        }
        return cl;
    }

}
