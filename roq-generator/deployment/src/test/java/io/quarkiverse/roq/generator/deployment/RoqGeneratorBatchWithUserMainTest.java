package io.quarkiverse.roq.generator.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * In batch mode, an application-provided main class takes precedence over the Roq generator main: the user must be
 * warned that the site will not be generated.
 */
public class RoqGeneratorBatchWithUserMainTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClass(UserMain.class))
            .overrideConfigKey("quarkus.roq.generator.batch", "true")
            .setLogRecordPredicate(r -> r.getLevel().equals(Level.WARNING)
                    && r.getLoggerName().equals(RoqGeneratorProcessor.class.getName()))
            .assertLogRecords(records -> {
                assertEquals(1, records.size());
                final String message = String.format(records.get(0).getMessage(), records.get(0).getParameters());
                assertTrue(message.contains(UserMain.class.getName()), message);
                assertTrue(message.contains("quarkus.roq.generator.batch"), message);
            });

    @Test
    public void testWarningLogged() {
        // assertions are done in assertLogRecords
    }

    @QuarkusMain
    public static class UserMain implements QuarkusApplication {
        @Override
        public int run(String... args) {
            return 0;
        }
    }
}
