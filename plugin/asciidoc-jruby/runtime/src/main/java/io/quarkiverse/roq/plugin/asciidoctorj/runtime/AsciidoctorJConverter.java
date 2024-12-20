package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.asciidoctor.*;
import org.jboss.logging.Logger;

@Singleton
public class AsciidoctorJConverter {

    private static final Logger LOG = Logger.getLogger(AsciidoctorJConverter.class);

    private final Asciidoctor asciidoctor;

    private final AsciidoctorJConfig config;
    private final Options options;

    @Inject
    public AsciidoctorJConverter(AsciidoctorJConfig config) {
        LOG.info("Starting Asciidoctorj...");
        final Instant start = Instant.now();
        this.asciidoctor = Asciidoctor.Factory.create();
        this.config = config;
        asciidoctor.requireLibrary("asciidoctor-diagram");
        final AttributesBuilder attributes = Attributes.builder().showTitle(true).attribute("noheader", true);
        config.icons().ifPresent(attributes::icons);
        config.sourceHighlighter().ifPresent(attributes::sourceHighlighter);
        config.imageDir().ifPresent(attributes::imagesDir);
        config.outputImageDir().ifPresent(d -> attributes.attribute("imagesoutdir", d));
        final Path templateDir = Paths.get(config.templatesDir());
        final OptionsBuilder optionsBuilder = Options.builder();
        if (Files.isDirectory(templateDir)) {
            optionsBuilder.templateDirs(templateDir.toFile());
            optionsBuilder.standalone(true);
        } else {
            optionsBuilder.standalone(false);
        }
        options = optionsBuilder
                .safe(SafeMode.SAFE)
                .backend("html5")
                .baseDir(Paths.get("").toFile())
                .attributes(attributes.build())
                .build();
        LOG.infof("Asciidoctorj started in %sms", Duration.between(start, Instant.now()).toMillis());
    }

    public String apply(String asciidoc) {
        // Cleaning the content from global indentation is necessary because
        // AsciiDoc content is not supposed to be indented globally
        // In Qute context it might often be indented
        return asciidoctor.convert(trimIndent(asciidoc), options);
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
