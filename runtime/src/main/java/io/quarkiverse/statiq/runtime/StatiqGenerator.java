package io.quarkiverse.statiq.runtime;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

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

@ApplicationScoped
public class StatiqGenerator implements Handler<RoutingContext> {
    private final Instance<Vertx> vertx;
    private final StatiqGeneratorConfig config;
    private WebClient client;
    private final List<StatiqPage> statiqPages;

    @Inject
    public StatiqGenerator(final Instance<Vertx> vertx, final StatiqGeneratorConfig config,
            @All final List<StatiqPages> statiqPages) {
        this.vertx = vertx;
        this.config = config;
        this.statiqPages = statiqPages.stream().map(StatiqPages::pages).flatMap(List::stream)
                .sorted(Comparator.comparing(StatiqPage::outputPath)).toList();
    }

    private WebClient client() {
        if (client == null) {
            client = WebClient.create(vertx.get());
        }
        return client;
    }

    @Override
    public void handle(RoutingContext event) {
        final FileSystem fs = vertx.get().fileSystem();
        final List<Uni<Void>> all = new ArrayList<>();
        final Path outputDir = Path.of(config.outputDir).toAbsolutePath();
        for (StatiqPage page : this.statiqPages) {
            all.add(Uni.createFrom().completionStage(() -> fetchContent(event.request(), page.path()))
                    .chain(r -> {
                        final Path targetPath = outputDir.resolve(page.outputPath());
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

    private CompletionStage<HttpResponse<Buffer>> fetchContent(HttpServerRequest request, String path) {
        return client().get(request.localAddress().port(), "localhost", path)
                .send().toCompletionStage();
    }

}
