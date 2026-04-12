package io.quarkiverse.roq.frontmatter.deployment.items.scan;

import java.nio.file.Path;

import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterTemplateUtils;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;

/**
 * All file-derived information extracted at scan time.
 */
public record FrontMatterTemplateMetadata(
        Path filePath,
        String referencePath,
        SourceFile sourceFile,
        RoqFrontMatterQuteMarkupBuildItem markup,
        RoqFrontMatterTemplateUtils.ParsedHeaders parsedHeaders,
        String templateId,
        String outputPath,
        boolean isHtml,
        boolean isPartial) {
}
