package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.roq.frontmatter.deployment.items.record.RoqFrontMatterSiteModelBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkus.builder.BuildContext;
import io.quarkus.builder.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Site: {@code files-site} (resource)
 * <p>
 * Features tested: the build-time site model exposed to other extensions is the same model the application
 * runs with, bound so that page expressions (images, collections) evaluate without a CDI container.
 * <p>
 * The consuming build step writes what it sees to a file under target/ since the augmentation and the test
 * do not share a classloader.
 */
@DisplayName("Roq FrontMatter - Build-time site model")
public class RoqFrontMatterSiteModelTest {

    static final Path REPORT = Path.of("target/site-model.txt");

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "files-site")
            .addBuildChainCustomizer(builder -> builder.addBuildStep(new ReportStep())
                    .consumes(RoqFrontMatterSiteModelBuildItem.class)
                    .produces(GeneratedResourceBuildItem.class)
                    .build())
            .withApplicationRoot((jar) -> jar.addAsResource("files-site"));

    public static class ReportStep implements BuildStep {
        @Override
        public void execute(BuildContext context) {
            RoqFrontMatterSiteModelBuildItem model = context.consume(RoqFrontMatterSiteModelBuildItem.class);
            DocumentPage firstDocument = model.documents().get(0);
            String report = String.join("\n",
                    "pages=" + model.pages().size(),
                    "documents=" + model.documents().size(),
                    "allPages=" + model.allPages().size(),
                    "siteImage=" + model.site().image("cover.png"),
                    "postCollection=" + firstDocument.collection().id(),
                    "documentImage=" + firstDocument.image(),
                    "bound=" + (model.pages().get(0).site() == model.site()));
            try {
                Files.writeString(REPORT, report, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            context.produce(new GeneratedResourceBuildItem("META-INF/site-model-test.txt",
                    report.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test
    @DisplayName("Model matches the site and is bound")
    void modelMatchesSite() throws IOException {
        Map<String, String> m = new HashMap<>();
        for (String line : Files.readAllLines(REPORT)) {
            m.put(line.substring(0, line.indexOf('=')), line.substring(line.indexOf('=') + 1));
        }
        assertEquals("/images/cover.png", m.get("siteImage"));
        assertEquals("true", m.get("bound"));
        assertEquals("posts", m.get("postCollection"));
        assertTrue(Integer.parseInt(m.get("pages")) >= 3, m.toString());
        assertEquals(3, Integer.parseInt(m.get("documents")), m.toString());
        assertEquals(Integer.parseInt(m.get("pages")) + 3, Integer.parseInt(m.get("allPages")), m.toString());
    }
}
