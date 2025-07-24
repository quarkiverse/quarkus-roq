package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import static org.asciidoctor.Options.BASEDIR;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.asciidoctor.*;
import org.asciidoctor.ast.Document;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateAttributes;

@Singleton
public class AsciidoctorJConverter {

    private static final Logger LOG = Logger.getLogger(AsciidoctorJConverter.class);

    private final Asciidoctor asciidoctor;
    private Map<String, String> configuredAttributes;

    @Inject
    public AsciidoctorJConverter(AsciidoctorJConfig config) {
        this(config.attributes());
    }

    public AsciidoctorJConverter(Map<String, String> configuredAttributes) {
        this.configuredAttributes = configuredAttributes;
        LOG.info("Starting Asciidoctorj...");
        final Instant start = Instant.now();
        this.asciidoctor = Asciidoctor.Factory.create();
        asciidoctor.requireLibrary("asciidoctor-diagram");
        asciidoctor.javaExtensionRegistry().includeProcessor(new AsciidocJInclude());
        LOG.infof("Asciidoctorj started in %sms", Duration.between(start, Instant.now()).toMillis());

    }

    public Options createOptions(RoqTemplateAttributes templateAttributes) {
        final AttributesBuilder attributes = Attributes.builder()
                .showTitle(true)
                .attribute("sitegen", "roq")
                .attribute("relfileprefix", "../")
                .attribute("relfilesuffix", "/")
                .attribute("noheader", true);
        if (templateAttributes.pageUrl() != null) {
            attributes.attribute("page-url", templateAttributes.pageUrl());
        }
        if (templateAttributes.pagePath() != null) {
            attributes.attribute("page-path", templateAttributes.pagePath());
        }
        if (templateAttributes.siteUrl() != null) {
            attributes.attribute("site-url", templateAttributes.siteUrl());
        }
        if (templateAttributes.sitePath() != null) {
            attributes.attribute("site-path", templateAttributes.sitePath());
        }
        configuredAttributes.forEach(attributes::attribute);

        final OptionsBuilder optionsBuilder = Options.builder();
        if (templateAttributes.sourcePath() != null) {
            Path templateDir = Paths.get(templateAttributes.sourcePath()).getParent();
            optionsBuilder.option(BASEDIR, templateDir.toAbsolutePath().toString());
        }
        return optionsBuilder
                .safe(SafeMode.SAFE)
                .backend("html5")
                .attributes(attributes.build())
                .build();
    }

    public String apply(String asciidoc,
            RoqTemplateAttributes attributes) {
        Options options = createOptions(attributes);
        // Cleaning the content from global indentation is necessary because
        // AsciiDoc content is not supposed to be indented globally
        // In Qute context it might often be indented
        final Document doc = asciidoctor.load(trimIndent(asciidoc), options);
        return doc.convert();
    }

    public static String trimIndent(String content) {
        int minIndent = Integer.MAX_VALUE;
        boolean foundNonEmptyLine = false;

        // Calculate minimum indentation in a single pass
        final String[] lines = content.split("\\v");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                int leadingSpaces = line.indexOf(line.trim());
                minIndent = Math.min(minIndent, leadingSpaces);
                foundNonEmptyLine = true;
            }
        }

        // If no indentation needs removal, or all lines are empty, return original content
        if (!foundNonEmptyLine || minIndent == 0) {
            return content;
        }

        // Build the output with trimmed indent
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            if (line.length() >= minIndent) {
                result.append(line.substring(minIndent));
            } else {
                result.append(line); // Preserve empty lines as-is
            }
            result.append("\n");
        }

        return result.toString();
    }

}
