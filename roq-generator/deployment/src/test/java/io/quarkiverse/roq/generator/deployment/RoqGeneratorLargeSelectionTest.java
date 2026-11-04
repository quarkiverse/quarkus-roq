package io.quarkiverse.roq.generator.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkiverse.roq.generator.runtime.RoqSelection;
import io.quarkiverse.roq.generator.runtime.SelectedPath;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A site with tens of thousands of paths must build: the selection must not be recorded as
 * constants of one generated class.
 */
public class RoqGeneratorLargeSelectionTest {

    static final int PATHS = 30_000;

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .addBuildChainCustomizer(builder -> builder.addBuildStep(new BuildStep() {
                @Override
                public void execute(BuildContext context) {
                    for (int i = 0; i < PATHS; i++) {
                        context.produce(new SelectedPathBuildItem("/section/page-" + i + "/", null));
                    }
                }
            }).produces(SelectedPathBuildItem.class).build());

    @Inject
    RoqSelection selection;

    @Test
    public void testAllPathsSelected() {
        long count = selection.paths().stream().map(SelectedPath::path).filter(p -> p.startsWith("/section/")).count();
        assertEquals(PATHS, count);
        assertTrue(selection.paths().stream().anyMatch(p -> p.path().equals("/section/page-" + (PATHS - 1) + "/")));
    }

}
