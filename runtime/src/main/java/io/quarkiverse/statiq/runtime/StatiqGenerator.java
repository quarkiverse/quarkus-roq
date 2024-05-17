package io.quarkiverse.statiq.runtime;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkus.arc.All;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

@Singleton
public class StatiqGenerator implements Handler<RoutingContext> {
    private final Instance<Vertx> vertx;
    private final StatiqGeneratorConfig config;
    private WebClient client;
    private final List<StatiqPage> statiqPages = new ArrayList<>();

    @Inject
    public StatiqGenerator(final Instance<Vertx> vertx, final StatiqGeneratorConfig config,
            @All final List<StatiqPages> statiqPages) {
        this.vertx = vertx;
        this.config = config;
        this.statiqPages.addAll(statiqPages.stream().map(StatiqPages::pages).flatMap(List::stream).toList());
    }

    private WebClient client() {
        if (client == null) {
            client = WebClient.create(vertx.get());
        }
        return client;
    }

    public void addFixedStaticPages(List<String> staticPaths) {
        for (String p : config.fixedPaths) {
            if (!isGlobPattern(p) && p.startsWith("/")) {
                // fixed paths are directly added
                this.statiqPages.add(new StatiqPage(p));
                continue;
            }
            // Try to detect fixed paths from glob pattern
            for (String staticPath : staticPaths) {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + p);
                if (matcher.matches(Path.of(staticPath))) {
                    this.statiqPages.add(new StatiqPage(staticPath));
                }
            }
        }
    }

    @Override
    public void handle(RoutingContext event) {
        final FileSystem fs = vertx.get().fileSystem();
        final List<Uni<Void>> all = new ArrayList<>();
        final Path outputDir = Path.of(config.outputDir).toAbsolutePath();
        final List<String> paths = this.statiqPages.stream().map(StatiqPage::path).toList();
        for (String path : paths) {
            all.add(Uni.createFrom().completionStage(() -> fetchContent(event.request(), path))
                    .chain(r -> {
                        final Path targetPath = computeStatiqPath(outputDir, path);
                        return Uni.createFrom()
                                .completionStage(() -> fs.mkdirs(targetPath.getParent().toString()).toCompletionStage())
                                .chain(() -> Uni.createFrom().completionStage(fs
                                        .writeFile(targetPath.toString(), r.bodyAsBuffer()).toCompletionStage()));
                    }));

        }

        Uni.createFrom().completionStage(() -> fs.exists(outputDir.toString()).compose(r -> {
            if (r) {
                return fs.deleteRecursive(outputDir.toString(), true);
            } else {
                return Future.succeededFuture();
            }
        }).toCompletionStage())
                .chain(() -> Uni.join().all(all).andFailFast())
                .subscribe().with(t -> {
                    event.response().setStatusCode(200);
                    event.response().end("Generated in: " + config.outputDir);
                }, event::fail);

    }

    private static Path computeStatiqPath(Path outputDir, String path) {
        String statiqPath = path;
        if (statiqPath.endsWith("/")) {
            statiqPath += "index.html";
        }
        if (statiqPath.startsWith("/")) {
            statiqPath = statiqPath.substring(1);
        }
        return outputDir.resolve(statiqPath).toAbsolutePath();
    }

    private CompletionStage<HttpResponse<Buffer>> fetchContent(HttpServerRequest request, String path) {
        return client().get(request.localAddress().port(), "localhost", path)
                .send().toCompletionStage();
    }

    private static boolean isGlobPattern(String s) {
        // Check if the string contains any glob pattern special characters
        return s.contains("*") || s.contains("?") || s.contains("{") || s.contains("}") || s.contains("[") || s.contains("]")
                || s.contains("**");
    }

}
