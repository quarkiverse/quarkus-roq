package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import static org.asciidoctor.Options.BASEDIR;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.IncludeProcessor;
import org.asciidoctor.extension.PreprocessorReader;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.runtime.util.ClassPathUtils;

public class AsciidocJIncludeProcessor extends IncludeProcessor {
    public static final Pattern URL_PREFIX_PATTERN = Pattern.compile("^((https?|file|ftp|irc)://|mailto:)");
    private final String contentDir;

    public AsciidocJIncludeProcessor(String contentDir) {
        this.contentDir = contentDir;
    }

    @Override
    public boolean handles(String target) {
        return URL_PREFIX_PATTERN.matcher(target).matches() && target.endsWith(".adoc") || target.endsWith(".asciidoc");
    }

    @Override
    public void process(Document document, PreprocessorReader reader, String target, Map<String, Object> attributes) {
        final String baseDir = document.getOptions().getOrDefault(BASEDIR, ".").toString();
        // This is most likely a classpath resource
        if (baseDir.startsWith(PathUtils.prefixWithSlash(contentDir))) {
            try {
                AtomicReference<String> content = new AtomicReference<>();
                ClassPathUtils.consumeAsPaths(PathUtils.join(baseDir, target), p -> {
                    try {
                        content.set(Files.readString(p));
                    } catch (IOException e) {
                        throw new RuntimeException("Can't read '" + target + "'");
                    }
                });
                if (content.get() == null) {
                    throw new RuntimeException("Include file not found '" + target + "'");
                }
                pushInclude(reader, target, attributes, content);
                return;
            } catch (IOException e) {
                throw new RuntimeException("Can't read '" + target + "'");
            }

        }
        Path p = Path.of(target);
        Path resolved = p.isAbsolute() ? p : Path.of(baseDir, target);
        if (Files.isRegularFile(resolved)) {
            try {
                reader.pushInclude(
                        Files.readString(resolved),
                        target,
                        p.toString(),
                        1,
                        attributes);
            } catch (IOException e) {
                throw new RuntimeException("Can't read '" + target + "'");
            }
        }
        throw new RuntimeException("Include file not found '" + resolved + "'");
    }

    private static void pushInclude(PreprocessorReader reader, String target, Map<String, Object> attributes,
            AtomicReference<String> content) {
        reader.pushInclude(
                content.get(),
                target,
                target,
                1,
                attributes);
    }

}
