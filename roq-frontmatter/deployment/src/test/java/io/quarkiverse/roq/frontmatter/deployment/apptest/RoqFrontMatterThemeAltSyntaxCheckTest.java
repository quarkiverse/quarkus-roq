package io.quarkiverse.roq.frontmatter.deployment.apptest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * Site: {@code basic-site} (resource), with a theme dependency whose templates use {@code {=expr}} expressions
 * <p>
 * Verifies that a warning is logged when the theme jar doesn't enable the alternative expression syntax in
 * {@code templates/.qute}, even if the site enables it.
 */
@DisplayName("Roq FrontMatter - Theme alt syntax check")
public class RoqFrontMatterThemeAltSyntaxCheckTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "basic-site")
            .overrideConfigKey("quarkus.qute.alt-expr-syntax", "true")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("basic-site"))
            .withAdditionalDependency(jar -> jar
                    .addAsResource(new StringAsset("<p>{=page.title}</p>"), "templates/partials/demo/title.html")
                    .addAsResource(new StringAsset("<p>{page.title}</p>"), "templates/partials/demo/standard.html"))
            .setLogRecordPredicate(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
            .assertLogRecords(records -> assertThat(records)
                    .map(record -> ((ExtLogRecord) record).getFormattedMessage())
                    .anySatisfy(message -> assertThat(message)
                            .contains("use {=...} expressions")
                            .contains("partials/demo/title.html")
                            .doesNotContain("partials/demo/standard.html")));

    @Test
    public void warnsAboutAltExpressionsWithoutAltSyntax() {
        // Asserted on the build log records
    }
}
