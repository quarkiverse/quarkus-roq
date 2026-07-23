package io.quarkiverse.roq.plugin.l10n.asciidoc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

class L10nAdocPoFileResolver {

    static Optional<Path> resolve(Path poBaseDir, String baseDir, String rootDir, String docName) {
        Path basePath = Paths.get(baseDir);
        Path rootPath = Paths.get(rootDir);
        Path sourcePath = basePath.resolve(docName + ".adoc");
        if (!sourcePath.startsWith(rootPath)) {
            return Optional.empty();
        }
        Path relativePath = rootPath.relativize(sourcePath);
        Path poFile = poBaseDir.resolve(relativePath + ".po");
        if (Files.exists(poFile)) {
            return Optional.of(poFile);
        }
        return Optional.empty();
    }
}
