package io.quarkiverse.roq.plugin.image.deployment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * A page image that does not exist fails the build with Roq's own "Image not found" error, not with the
 * reflection wrapper Qute puts around it.
 */
public class RoqImageMissingImageTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "missing-image-site")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("missing-image-site"))
            .assertException(t -> {
                String messages = BuildFailures.messages(t);
                assertTrue(messages.contains("Image not found"), messages);
                assertTrue(messages.contains("missing.jpg"), messages);
                // where the usage is, and the page that declares the image
                assertTrue(messages.contains("page.image"), messages);
                assertTrue(messages.contains("{#img} at line"), messages);
                assertTrue(messages.contains("content/index.html"), messages);
                assertFalse(messages.contains("Reflection invocation error"), messages);
            });

    @Test
    public void testMissingImageFailsTheBuildCleanly() {
        // the assertion is in assertException
    }
}
