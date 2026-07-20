package io.quarkus.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class JekyllPluginConverter {

    enum Classification {
        HANDLED,
        TRANSLATABLE,
        MANUAL
    }

    record PluginInfo(Classification classification, String reason, String equivalentFile) {
        PluginInfo(Classification classification, String reason) {
            this(classification, reason, null);
        }
    }

    record Result(
            List<String> handled,
            List<String> translated,
            List<String> skipped,
            List<String> failed,
            Map<String, String> failureMessages) {
    }

    // Only truly generic plugins that apply to ALL Jekyll sites.
    // Site-specific plugins should be classified as MANUAL and fail the build,
    // guiding users to implement custom equivalents.
    private static final Map<String, PluginInfo> KNOWN_PLUGINS = Map.of(
            "cname.rb", new PluginInfo(Classification.HANDLED,
                    "CNAME value is configured in siteConfig.yml (GitHub Pages convention)"));

    private static final String JAVA_PACKAGE = "io.quarkus.tools.migration";
    private static final String JAVA_PACKAGE_PATH = "src/main/java/io/quarkus/tools/migration";

    private final Path projectDir;

    public JekyllPluginConverter(Path projectDir) {
        this.projectDir = projectDir;
    }

    public Result convert() throws IOException {
        Path pluginsDir = projectDir.resolve("_plugins");
        List<String> handled = new ArrayList<>();
        List<String> translated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<String, String> failureMessages = new LinkedHashMap<>();

        if (!Files.isDirectory(pluginsDir)) {
            return new Result(handled, translated, skipped, failed, failureMessages);
        }

        List<Path> rubyFiles;
        try (Stream<Path> paths = Files.list(pluginsDir)) {
            rubyFiles = paths
                    .filter(p -> p.getFileName().toString().endsWith(".rb"))
                    .sorted()
                    .toList();
        }

        for (Path rbFile : rubyFiles) {
            String fileName = rbFile.getFileName().toString();
            String rbContent = Files.readString(rbFile);

            PluginInfo info = KNOWN_PLUGINS.get(fileName);
            if (info == null) {
                info = classifyUnknownPlugin(fileName, rbContent);
            }

            switch (info.classification()) {
                case HANDLED -> {
                    System.out.println("  [HANDLED] " + fileName + " — " + info.reason());
                    handled.add(fileName);
                }
                case TRANSLATABLE -> {
                    Path equivalentPath = projectDir.resolve(JAVA_PACKAGE_PATH)
                            .resolve(info.equivalentFile());
                    if (Files.exists(equivalentPath)) {
                        System.out.println("  [SKIP] " + fileName
                                + " — equivalent already exists: " + info.equivalentFile());
                        skipped.add(fileName);
                    } else {
                        translatePlugin(fileName, rbContent, info);
                        System.out.println("  [TRANSLATED] " + fileName
                                + " → " + info.equivalentFile());
                        translated.add(fileName);
                    }
                }
                case MANUAL -> {
                    Path equivalentPath = projectDir.resolve(JAVA_PACKAGE_PATH)
                            .resolve(info.equivalentFile());
                    if (Files.exists(equivalentPath)) {
                        System.out.println("  [SKIP] " + fileName
                                + " — equivalent already exists: " + info.equivalentFile());
                        skipped.add(fileName);
                    } else {
                        String msg = buildManualFailureMessage(fileName, rbContent, info);
                        failureMessages.put(fileName, msg);
                        failed.add(fileName);
                        System.err.println("  [MANUAL] " + fileName + " — needs hand-coded equivalent");
                    }
                }
            }
        }

        return new Result(handled, translated, skipped, failed, failureMessages);
    }

    private PluginInfo classifyUnknownPlugin(String fileName, String content) {
        String javaName = rubyFileToJavaClass(fileName) + ".java";
        return new PluginInfo(Classification.MANUAL,
                "Unknown Jekyll plugin — needs manual migration", javaName);
    }

    private String rubyFileToJavaClass(String rbFileName) {
        String base = rbFileName.replaceAll("\\.rb$", "");
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : base.toCharArray()) {
            if (c == '-' || c == '_') {
                capitalize = true;
            } else {
                sb.append(capitalize ? Character.toUpperCase(c) : c);
                capitalize = false;
            }
        }
        return sb.toString();
    }

    private void translatePlugin(String fileName, String rbContent, PluginInfo info) throws IOException {
        // Currently no plugins support automatic translation.
        // Site-specific plugins should be added to KNOWN_PLUGINS as TRANSLATABLE
        // with custom translation logic here.
        throw new IllegalStateException("No translation logic for: " + fileName);
    }

    private String buildManualFailureMessage(String fileName, String rbContent, PluginInfo info) {
        StringBuilder msg = new StringBuilder();
        msg.append("Jekyll plugin '").append(fileName).append("' requires manual migration.\n");
        msg.append("Create: ").append(JAVA_PACKAGE_PATH).append("/").append(info.equivalentFile()).append("\n\n");
        msg.append("Plugin contents:\n");

        String[] lines = rbContent.split("\n");
        for (int i = 0; i < Math.min(lines.length, 20); i++) {
            msg.append("  ").append(lines[i]).append("\n");
        }
        if (lines.length > 20) {
            msg.append("  ... (").append(lines.length - 20).append(" more lines)\n");
        }

        return msg.toString();
    }
}
