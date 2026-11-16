package io.quarkiverse.roq.plugin.image.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * An image expression rooted in data the plugin does not own ({@code photo.src} in a gallery loop) is not a
 * page or document expression: the build warns and leaves it to the runtime lookup (fed by
 * {@code quarkus.qute.image.generate} globs), instead of failing.
 */
public class RoqImageDataRootTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "data-root-site")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addAsResource("data-root-site"));

    @Test
    public void testBuildPasses() {
        // the assertion is the build itself
    }
}
