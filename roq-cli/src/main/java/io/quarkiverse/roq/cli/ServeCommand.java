package io.quarkiverse.roq.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import io.quarkus.cli.common.OutputOptionMixin;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "serve", mixinStandardHelpOptions = true, description = "Serve a static site directory")
public class ServeCommand implements Callable<Integer> {

    @CommandLine.Mixin(name = "output")
    OutputOptionMixin output;

    @Parameters(index = "0", defaultValue = "target/roq/", description = "Directory to serve")
    private String directory;

    @Option(names = { "-p", "--port" }, defaultValue = "8181", description = "Port to listen on")
    private int port;

    @Option(names = { "--cache" }, negatable = true, defaultValue = "false", description = "Enable/disable caching")
    private boolean cache;

    @Override
    public Integer call() {
        Path dirPath = Path.of(directory).toAbsolutePath();
        if (!Files.isDirectory(dirPath)) {
            output.error("Directory not found: " + dirPath);
            return CommandLine.ExitCode.SOFTWARE;
        }

        output.info("Serving: " + dirPath);

        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        CountDownLatch latch = new CountDownLatch(1);
        int[] exitCode = { CommandLine.ExitCode.OK };

        if (!cache) {
            router.route().handler(ctx -> {
                ctx.response().putHeader("Cache-Control", "no-store, no-cache, must-revalidate");
                ctx.next();
            });
        }
        router.route().handler(
                StaticHandler.create(FileSystemAccess.ROOT, dirPath.toString())
                        .setCachingEnabled(cache));

        Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown));

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(s -> output.info("Server started on http://localhost:" + port))
                .onFailure(e -> {
                    output.error("Failed to start server: " + e.getMessage());
                    exitCode[0] = CommandLine.ExitCode.SOFTWARE;
                    latch.countDown();
                });

        try {
            latch.await();
        } catch (InterruptedException e) {
            // Shutting down
        } finally {
            vertx.close();
        }

        return exitCode[0];
    }
}
