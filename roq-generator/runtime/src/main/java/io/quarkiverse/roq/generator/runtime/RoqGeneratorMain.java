package io.quarkiverse.roq.generator.runtime;

import java.net.ConnectException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkus.runtime.QuarkusApplication;

/**
 * Application entry point used when {@code quarkus.roq.generator.batch=true}: generates the static site once the
 * application (and its HTTP server) is started, then exits.
 * <p>
 * Registered by the extension rather than annotated with {@code @QuarkusMain}, so that an application-provided main
 * class takes precedence and dev mode keeps the default main (staying up until stopped).
 */
@Singleton
public class RoqGeneratorMain implements QuarkusApplication {

    private static final Logger LOGGER = Logger.getLogger(RoqGeneratorMain.class);

    @Inject
    RoqGenerator generator;

    @Override
    public int run(String... args) {
        try {
            generator.generateBlocking();
            LOGGER.info("Roq generation succeeded in directory: " + generator.outputDir());
            return 0;
        } catch (Exception e) {
            if (e instanceof ConnectException || e.getCause() instanceof ConnectException) {
                LOGGER.error("Roq generation failed");
            } else {
                LOGGER.error("Roq generation failed", e);
            }
            return 1;
        }
    }
}
