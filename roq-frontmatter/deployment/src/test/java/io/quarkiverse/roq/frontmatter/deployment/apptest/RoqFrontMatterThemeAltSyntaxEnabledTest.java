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
 * Site: {@code basic-site} (resource), with a theme dependency whose templates use {@code {=expr}} expressions and
 * which enables the alternative expression syntax in {@code templates/.qute}
 * <p>
 * Verifies that no alt syntax warning is logged.
 */
@DisplayName("Roq FrontMatter - Theme alt syntax enabled")
public class RoqFrontMatterThemeAltSyntaxEnabledTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.roq.resource-dir", "basic-site")
            .withApplicationRoot((jar) -> jar
                    .addAsResource("basic-site"))
            .withAdditionalDependency(jar -> jar
                    .addAsResource(new StringAsset("alt-expr-syntax=true\n"), "templates/.qute")
                    .addAsResource(new StringAsset("<p>{=page.title}</p>"), "templates/partials/demo/title.html"))
            .setLogRecordPredicate(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
            .assertLogRecords(records -> assertThat(records)
                    .map(record -> ((ExtLogRecord) record).getFormattedMessage())
                    .noneMatch(message -> message.contains("use {=...} expressions")));

    @Test
    public void noWarningWhenAltSyntaxIsEnabled() {
        // Asserted on the build log records
    }
}
