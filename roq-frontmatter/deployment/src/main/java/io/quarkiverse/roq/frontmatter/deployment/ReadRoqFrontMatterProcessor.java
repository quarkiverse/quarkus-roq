package io.quarkiverse.roq.frontmatter.deployment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jboss.logging.Logger;
import org.yaml.snakeyaml.Yaml;

import io.quarkiverse.roq.frontmatter.deployment.items.RoqFrontMatterBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.runtime.util.ClassPathUtils;
import io.vertx.core.json.JsonObject;

public class ReadRoqFrontMatterProcessor {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(ReadRoqFrontMatterProcessor.class);
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".md", ".html", ".asciidoc", ".adoc");
    public static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\n.*\\n---", Pattern.DOTALL);

    @BuildStep
    void scanDataFiles(BuildProducer<RoqFrontMatterBuildItem> dataProducer,
            RoqFrontMatterConfig roqDataConfig) {
        try {
            Set<RoqFrontMatterBuildItem> items = scanDataFiles(roqDataConfig.locations());

            for (RoqFrontMatterBuildItem item : items) {
                dataProducer.produce(item);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<RoqFrontMatterBuildItem> scanDataFiles(List<String> locations) throws IOException {

        HashSet<RoqFrontMatterBuildItem> items = new HashSet<>();
        for (String location : locations) {
            final String finalLocation = buildFinalLocation(location);
            String resourcePath = "site/" + finalLocation;

            ClassPathUtils.consumeAsPaths(resourcePath, (path) -> {
                if (Files.isDirectory(path)) {
                    try (Stream<Path> pathStream = Files.walk(path)) {
                        pathStream
                                .filter(Files::isRegularFile)
                                .filter(isExtensionSupported())
                                .forEach(addBuildItem(items, finalLocation));

                    } catch (IOException e) {
                        throw new RuntimeException("Was not possible to scan data files on location %s".formatted(location), e);
                    }
                }
            });
        }
        return items;
    }

    private static Consumer<Path> addBuildItem(HashSet<RoqFrontMatterBuildItem> items, String location) {
        return file -> {
            String path = location.equals("layout") ? file.getFileName().toString()
                    : location + "/" + file.getFileName().toString();

            try {
                final String fullContent = Files.readString(file, StandardCharsets.UTF_8);
                if (hasFrontMatter(fullContent)) {
                    Yaml yaml = new Yaml();
                    Map<String, Object> map = yaml.loadAs(getFrontMatter(fullContent), Map.class);
                    final JsonObject fm = new JsonObject(map);
                    final String layout = normalizedLayout(fm.getString("layout"));
                    final String content = stripFrontMatter(fullContent);
                    LOGGER.debugf("Creating generated template for %s" + path);
                    final String generatedTemplate = generateTemplate(layout, content);
                    items.add(new RoqFrontMatterBuildItem(path, layout, fm, generatedTemplate));
                } else {
                    items.add(new RoqFrontMatterBuildItem(path, null, new JsonObject(), fullContent));
                }
            } catch (IOException e) {
                throw new RuntimeException("Error while reading the FrontMatter file %s"
                        .formatted(path), e);
            }
        };
    }

    private static String generateTemplate(String layout, String content) {
        StringBuilder template = new StringBuilder();
        if (layout != null) {
            template.append("{#include ").append(layout).append("}\n");
        }
        template.append(content);
        template.append("\n{/include}");
        return template.toString();
    }

    private static String normalizedLayout(String layout) {
        if (layout == null) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        b.append(layout);
        if (!layout.endsWith(".html")) {
            b.append(".html");
        }
        return b.toString();
    }

    private static Predicate<Path> isExtensionSupported() {
        return file -> {
            String fileName = file.getFileName().toString();
            return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
        };
    }

    private String buildFinalLocation(String location) {
        while (location.startsWith("/")) {
            location = location.substring(1);
        }
        return location;
    }

    private static String getFrontMatter(String content) {
        int endOfFrontMatter = content.indexOf("---", 3);
        if (endOfFrontMatter != -1) {
            return content.substring(3, endOfFrontMatter).trim();
        }
        return "";
    }

    private static String stripFrontMatter(String content) {
        return FRONTMATTER_PATTERN.matcher(content).replaceAll("");
    }

    private static boolean hasFrontMatter(String content) {
        return FRONTMATTER_PATTERN.matcher(content).find();
    }
}
