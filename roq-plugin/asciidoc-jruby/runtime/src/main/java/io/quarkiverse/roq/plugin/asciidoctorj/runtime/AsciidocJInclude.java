package io.quarkiverse.roq.plugin.asciidoctorj.runtime;

import static io.quarkiverse.roq.plugin.asciidoctorj.runtime.AsciidoctorJConverter.ROOTDIR;
import static org.asciidoctor.Options.BASEDIR;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.asciidoctor.SafeMode;
import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.IncludeProcessor;
import org.asciidoctor.extension.PreprocessorReader;
import org.asciidoctor.log.LogRecord;
import org.asciidoctor.log.Severity;

import io.quarkiverse.roq.util.PathUtils;

public class AsciidocJInclude extends IncludeProcessor {
    private static final Pattern URL_PREFIX_PATTERN = Pattern.compile("^((https?|file|ftp|irc)://|mailto:)");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(=+)(\\s+.*)$");

    public AsciidocJInclude() {

    }

    @Override
    public boolean handles(String target) {
        return !URL_PREFIX_PATTERN.matcher(target).matches() && (target.endsWith(".adoc") || target.endsWith(".asciidoc"));
    }

    @Override
    public void process(Document document, PreprocessorReader reader, String target, Map<String, Object> attributes) {
        long safeLevel = (Long) document.getOptions().get("safe");
        if (safeLevel >= SafeMode.SECURE.getLevel()) {
            throw new SecurityException("File includes are not allowed in SECURE mode.");
        }

        final String dir = reader.getDir();
        Charset charset = Charset.forName((String) document.getAttributes().getOrDefault("encoding", "UTF-8"));
        final Path baseDir = Path.of(document.getOptions().getOrDefault(BASEDIR, "").toString());
        final Path rootDir = Path.of(document.getOptions().getOrDefault(ROOTDIR, "").toString());
        Path p = Path.of(target);
        Path targetPath = p.isAbsolute() ? p : baseDir.resolve(dir).resolve(target).normalize();

        if (safeLevel >= SafeMode.SAFE.getLevel()) {
            if (!targetPath.startsWith(rootDir.normalize())) {
                throw new SecurityException("Include path is outside the root dir ('%s'): '%s'".formatted(rootDir, targetPath));
            }
        }

        String resourcePath = PathUtils.toUnixPath(targetPath.toString());
        try (InputStream resource = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (resource != null) {
                pushInclude(reader, new String(resource.readAllBytes(), charset), target, attributes);
                return;
            }
        } catch (IOException e) {
            log(new LogRecord(Severity.ERROR, "Can't read '" + target + "'"));
            return;
        }

        if (!Files.isRegularFile(targetPath)) {
            log(new LogRecord(Severity.ERROR, "Include file not found '" + target + "'"));
            return;
        }
        try {
            pushInclude(reader, Files.readString(targetPath, charset), target, attributes);
        } catch (IOException e) {
            log(new LogRecord(Severity.ERROR, "Can't read '" + target + "'"));
        }

    }

    private static void pushInclude(PreprocessorReader reader, String content, String target, Map<String, Object> attributes)
            throws IOException {
        final String processedContent = String.join("\n", processInclude(content, attributes));
        reader.pushInclude(
                processedContent,
                target,
                target,
                1,
                attributes);
    }

    // The main entry point, based on the Ruby push_include logic
    static List<String> processInclude(String content, Map<String, Object> attrs) throws IOException {
        // 1. Handle encoding (applies to file reading, not in-memory)
        List<String> lines = content.lines().toList();

        // 2. Handle tag/tag(s) extraction first, as in Ruby
        if (attrs.containsKey("tag") && attrs.get("tag") instanceof String) {
            lines = extractTag(lines, (String) attrs.get("tag"));
        } else if (attrs.containsKey("tags") && attrs.get("tags") instanceof String) {
            // Ruby supports "tags" as a comma-separated list
            final String tags = (String) attrs.get("tags");
            for (String tag : tags.split(",")) {
                lines = extractTag(lines, tag.trim());
            }
        }

        // 3. Handle lines attribute (after tags, as in Ruby)
        if (attrs.containsKey("lines") && attrs.get("lines") instanceof String) {
            lines = extractLines(lines, (String) attrs.get("lines"));
        }

        // 4. Handle indent
        if (attrs.containsKey("indent") && attrs.get("indent") instanceof String) {
            int indent = Integer.parseInt((String) attrs.get("indent"));
            String pad = " ".repeat(indent);
            lines = lines.stream().map(line -> pad + line).toList();
        }

        return lines;
    }

    // --- Helper methods: direct port of Ruby logic ---

    // Extract lines between tag::...[] and end::...[]
    private static List<String> extractTag(List<String> lines, String tag) {
        // Ruby: start = /^\/\/\s*tag::#{tag}\[\]/
        //       end   = /^\/\/\s*end::#{tag}\[\]/
        Pattern start = Pattern.compile("^//\\s*tag::" + Pattern.quote(tag) + "\\[\\]");
        Pattern end = Pattern.compile("^//\\s*end::" + Pattern.quote(tag) + "\\[\\]");
        List<String> result = new ArrayList<>();
        boolean inTag = false;
        for (String line : lines) {
            if (!inTag && start.matcher(line).find()) {
                inTag = true;
                continue; // skip the tag line itself
            } else if (inTag && end.matcher(line).find()) {
                break; // stop after end tag
            }
            if (inTag) {
                result.add(line);
            }
        }
        return result;
    }

    // Extract specific lines or ranges, e.g. "1..3;5"
    private static List<String> extractLines(List<String> lines, String lineRanges) {
        Set<Integer> indices = new LinkedHashSet<>();
        for (String part : lineRanges.split("[;,]")) {
            part = part.trim();
            if (part.matches("\\d+")) {
                indices.add(Integer.parseInt(part) - 1);
            } else if (part.matches("\\d+\\.\\.\\d+")) {
                String[] range = part.split("\\.\\.");
                int start = Integer.parseInt(range[0]) - 1;
                int end = Integer.parseInt(range[1]) - 1;
                for (int i = start; i <= end; i++) {
                    indices.add(i);
                }
            }
        }
        List<String> result = new ArrayList<>();
        for (int idx : indices) {
            if (idx >= 0 && idx < lines.size()) {
                result.add(lines.get(idx));
            }
        }
        return result;
    }

    // Adjust section heading levels (e.g., = Foo → == Foo with leveloffset=+1)
    private static List<String> applyLevelOffset(List<String> lines, String leveloffset) {
        int offset = 0;
        if (leveloffset.startsWith("+") || leveloffset.startsWith("-")) {
            offset = Integer.parseInt(leveloffset);
        } else {
            offset = Integer.parseInt(leveloffset);
        }
        List<String> result = new ArrayList<>();
        Pattern heading = HEADING_PATTERN;
        for (String line : lines) {
            Matcher m = heading.matcher(line);
            if (m.matches()) {
                int level = m.group(1).length() + offset;
                level = Math.max(1, level);
                String newLine = "=".repeat(level) + m.group(2);
                result.add(newLine);
            } else {
                result.add(line);
            }
        }
        return result;
    }

}
