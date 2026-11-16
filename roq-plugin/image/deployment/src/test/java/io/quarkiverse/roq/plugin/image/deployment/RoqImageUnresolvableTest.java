package io.quarkiverse.roq.plugin.image.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * An image expression the build cannot evaluate in any of its contexts fails the build, naming the expression.
 */
public class RoqImageUnresolvableTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "unresolvable-site")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("unresolvable-site"))
            .assertException(t -> {
                String messages = BuildFailures.messages(t);
                assertTrue(messages.contains("Unresolvable image expression"), messages);
                assertTrue(messages.contains("page.data.get('nothing').image"), messages);
            });

    @Test
    public void testUnresolvableExpressionFailsTheBuild() {
        // the assertion is in assertException
    }
}
