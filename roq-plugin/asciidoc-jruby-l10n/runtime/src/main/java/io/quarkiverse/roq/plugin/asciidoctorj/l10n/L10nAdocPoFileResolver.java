package io.quarkiverse.roq.plugin.asciidoctorj.l10n;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import io.quarkiverse.roq.plugin.asciidoc.common.runtime.RoqAsciidocKeys;

class L10nAdocPoFileResolver {

    static Path computePoPath(Path poBaseDir, Path rootPath, Path sourcePath) {
        if (!sourcePath.startsWith(rootPath)) {
            return null;
        }
        Path relativePath = rootPath.relativize(sourcePath);
        return poBaseDir.resolve(relativePath + ".po");
    }

    static Optional<Path> findPoFileForDocName(Path poBaseDir, String baseDir, String rootDir, String docName) {
        Path basePath = Paths.get(baseDir);
        Path rootPath = Paths.get(rootDir);

        for (String ext : RoqAsciidocKeys.ASCIIDOC_EXTENSIONS) {
            Path sourcePath = basePath.resolve(docName + "." + ext);
            Path poPath = computePoPath(poBaseDir, rootPath, sourcePath);
            if (poPath != null && Files.exists(poPath)) {
                return Optional.of(poPath);
            }
        }
        return Optional.empty();
    }
}
