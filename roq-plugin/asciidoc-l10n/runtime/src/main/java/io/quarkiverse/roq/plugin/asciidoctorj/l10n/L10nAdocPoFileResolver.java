package io.quarkiverse.roq.plugin.asciidoctorj.l10n;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import io.quarkiverse.roq.plugin.asciidoc.common.runtime.RoqAsciidocKeys;
import io.quarkiverse.tools.stringpaths.StringPaths;

class L10nAdocPoFileResolver {

    static Path computePoPath(Path poBaseDir, Path rootPath, Path sourcePath) {
        if (!sourcePath.startsWith(rootPath)) {
            return null;
        }
        Path relativePath = rootPath.relativize(sourcePath);
        return poBaseDir.resolve(relativePath + ".po");
    }

    static boolean poFileExists(Path poPath) {
        // Try classpath first
        String resourcePath = StringPaths.toUnixPath(poPath.toString());
        try (InputStream resource = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (resource != null) {
                return true;
            }
        } catch (Exception e) {
            // This is just an existence check, so ignore and fall back to filesystem
        }

        // If it wasn't in the classpath, see what the filesystem has
        return Files.exists(poPath);
    }

    static Optional<Path> findPoFileForDocName(Path poBaseDir, String baseDir, String rootDir, String docName) {
        Path basePath = Paths.get(baseDir);
        Path rootPath = Paths.get(rootDir);

        for (String ext : RoqAsciidocKeys.ASCIIDOC_EXTENSIONS) {
            Path sourcePath = basePath.resolve(docName + "." + ext);
            Path poPath = computePoPath(poBaseDir, rootPath, sourcePath);
            if (poPath != null && poFileExists(poPath)) {
                return Optional.of(poPath);
            }
        }
        return Optional.empty();
    }
}
