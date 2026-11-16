package io.quarkiverse.roq.plugin.image.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * An expression usage with a preset that is not configured fails the build with a Roq error naming the template
 * and the location, before qute-web gets to it.
 */
public class RoqImageUnknownPresetTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "unknown-preset-site")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("unknown-preset-site"))
            .assertException(t -> {
                String messages = BuildFailures.messages(t);
                assertTrue(messages.contains("Unknown image preset"), messages);
                assertTrue(messages.contains("'nope'"), messages);
                assertTrue(messages.contains("{#img} at line"), messages);
                assertTrue(messages.contains("roq-hero"), messages);
            });

    @Test
    public void testUnknownPresetFailsTheBuild() {
        // the assertion is in assertException
    }
}
