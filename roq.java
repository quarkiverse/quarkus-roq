//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.vertx:vertx-core:4.5.7
//DEPS io.vertx:vertx-web:4.5.7
//DEPS io.netty:netty-all:4.1.109.Final

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import java.nio.file.Path;
import java.nio.file.Files;

public class roq {

    public static void main(String[] args) {

        String directory = args.length > 0 ?  args[0] : "target/roq/";

        if (!Files.isDirectory(Path.of(directory))) {
            System.err.println("Directory not found: " + directory);
            System.exit(1);
        }

        System.out.println("Serving: " + directory);

        String port = args.length > 1 ? args[1]: "8181";

        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);

        router.route().handler(StaticHandler.create().setWebRoot(directory));

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(Integer.parseInt(port), result -> {
                    if (result.succeeded()) {
                        System.out.println("Server started on port http://localhost:" + port);
                    } else {
                        System.err.println("Failed to start server: " + result.cause());
                    }
                });
    }
}