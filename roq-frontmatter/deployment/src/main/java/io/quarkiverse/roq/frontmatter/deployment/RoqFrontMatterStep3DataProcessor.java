package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterTemplateUtils.getLayoutKey;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqFrontMatterReadingException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqLayoutNotFoundException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqSiteScanningException;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterAttachment;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawLayoutBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDocumentBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterLayoutTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterPageTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterPaginatePageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishNormalPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.util.TemplateLink;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterStep3DataProcessor {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqFrontMatterStep3DataProcessor.class);
    public static final String LINK_KEY = "link";
    public static final String PAGINATE_KEY = "paginate";
    public static final String DRAFT_KEY = "draft";
    private static final String DATE_KEY = "date";
    public static final Pattern FILE_NAME_DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-");

    @BuildStep
    void prepareData(VertxHttpBuildTimeConfig httpConfig,
            RoqSiteConfig config,
            BuildProducer<RoqFrontMatterRootUrlBuildItem> rootUrlProducer,
            BuildProducer<RoqFrontMatterLayoutTemplateBuildItem> layoutTemplateProducer,
            BuildProducer<RoqFrontMatterPageTemplateBuildItem> pageTemplatesProducer,
            List<RoqFrontMatterRawLayoutBuildItem> rawLayouts,
            List<RoqFrontMatterRawPageBuildItem> rawPages) {
        if (rawLayouts.isEmpty() && rawPages.isEmpty()) {
            return;
        }

        if (LOGGER.isDebugEnabled()) {
            final List<TemplateDebugPrinter.DebugTemplateEntry> entries = getDebugTemplateEntries(rawLayouts, rawPages);
            LOGGER.debug("Template definition: \n\n" + TemplateDebugPrinter.buildTreeString(entries));
        }

        // Index layouts by id — used both for parent layout resolution and duplicate detection
        final var layoutsById = rawLayouts.stream()
                .collect(Collectors.toMap(RoqFrontMatterRawLayoutBuildItem::id, Function.identity(), (a, b) -> {
                    throw new IllegalStateException(
                            """

                                    Multiple layouts found with id '%s'.
                                     - '%s'
                                     - '%s'
                                    This usually happens when more than one 'layouts' directory provides a template with the same id. Please ensure layout IDs are unique across all themes and sources.
                                    """
                                    .formatted(a.id(), a.templateSource().file().absolutePath(),
                                            b.templateSource().file().absolutePath()));
                }));
        final RootUrl rootUrl = new RootUrl(config.urlOptional().orElse(""), httpConfig.rootPath());
        rootUrlProducer.produce(new RoqFrontMatterRootUrlBuildItem(rootUrl));

        // Process layouts: merge front matter from parent layouts (child values override parents)
        for (RoqFrontMatterRawLayoutBuildItem item : rawLayouts) {
            JsonObject data = mergeParents(config, item.layout(), item.data(),
                    item.templateSource().file().absolutePath(), layoutsById);
            layoutTemplateProducer.produce(new RoqFrontMatterLayoutTemplateBuildItem(item, data));
        }

        // Process pages: merge parent data, then filter by date/draft rules
        for (RoqFrontMatterRawPageBuildItem item : rawPages) {
            JsonObject data = mergeParents(config, item.layout(), item.data(),
                    item.templateSource().file().absolutePath(), layoutsById);

            // Parse date from front matter "date" key, or from filename pattern (e.g. 2024-03-10-my-post)
            ZonedDateTime date = parsePublishDate(item.templateSource().path(), data, config.dateFormat(),
                    config.timeZoneOrDefault());
            final boolean noFuture = !config.future() && (item.collection() == null || !item.collection().future());
            ZonedDateTime now = ZonedDateTime.now();
            if (date != null && noFuture && date.isAfter(now)) {
                LOGGER.warnf("Ignoring page '%s' because it's scheduled for later (%s > %s)." +
                        " To display future articles, use -Dsite.future=true%s.", item.templateSource().path(), date, now,
                        item.collection() == null ? ""
                                : " or -Dsite.collections.%s.future=true".formatted(item.collection().id()));
                continue;
            }

            String dateString = date.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
            final boolean draft = Boolean.parseBoolean(data.getString(DRAFT_KEY, "false"));
            if (!config.draft() && draft) {
                continue;
            }
            final PageFiles files = item.attachments() != null
                    ? new PageFiles(item.attachments().stream().map(RoqFrontMatterAttachment::name).toList(),
                            config.slugifyFiles())
                    : null;
            final PageSource pageSource = new PageSource(item.templateSource(), draft, dateString, files, false);
            final String link = TemplateLink.pageLink(config.pathPrefixOrEmpty(),
                    data.getString(LINK_KEY),
                    new TemplateLink.PageLinkData(pageSource, item.collectionId(), data));
            RoqUrl url = rootUrl.resolve(link);

            pageTemplatesProducer.produce(new RoqFrontMatterPageTemplateBuildItem(item, data, pageSource, url));
        }
    }

    private static List<TemplateDebugPrinter.DebugTemplateEntry> getDebugTemplateEntries(
            List<RoqFrontMatterRawLayoutBuildItem> rawLayouts, List<RoqFrontMatterRawPageBuildItem> rawPages) {
        List<TemplateDebugPrinter.DebugTemplateEntry> entries = new ArrayList<>();
        for (RoqFrontMatterRawLayoutBuildItem l : rawLayouts) {
            entries.add(new TemplateDebugPrinter.DebugTemplateEntry(
                    l.templateSource().path(),
                    l.isThemeLayout() ? "THEME_LAYOUT" : "LAYOUT",
                    null));
        }
        for (RoqFrontMatterRawPageBuildItem p : rawPages) {
            entries.add(new TemplateDebugPrinter.DebugTemplateEntry(
                    p.templateSource().path(),
                    p.collection() != null ? "DOCUMENT_PAGE" : "NORMAL_PAGE",
                    p.attachments()));
        }
        return entries;
    }

    // Dispatch pages by type: collection documents, paginated pages, or normal pages.
    // This determines how each page flows through the rest of the pipeline.
    @BuildStep
    void dispatchByType(BuildProducer<RoqFrontMatterPublishNormalPageBuildItem> pagesProducer,
            BuildProducer<RoqFrontMatterDocumentBuildItem> documentTemplatesProducer,
            BuildProducer<RoqFrontMatterPaginatePageBuildItem> paginatedPagesProducer,
            BuildProducer<RoqFrontMatterStaticFileBuildItem> staticFileProducer,
            List<RoqFrontMatterPageTemplateBuildItem> templates) {
        if (templates.isEmpty()) {
            return;
        }

        for (RoqFrontMatterPageTemplateBuildItem item : templates) {
            if (item.raw().attachments() != null) {
                // Publish static assets
                for (RoqFrontMatterAttachment attachment : item.raw().attachments()) {
                    staticFileProducer.produce(new RoqFrontMatterStaticFileBuildItem(
                            item.url().resolve(attachment.name()).resourcePath(), attachment.path()));
                }
            }
            // Prepare Documents and Paginated pages
            if (item.raw().collection() != null) {
                documentTemplatesProducer
                        .produce(new RoqFrontMatterDocumentBuildItem(item));
            } else {
                if (item.data().containsKey(PAGINATE_KEY)) {
                    // Pagination needs collections size so it's produced after
                    paginatedPagesProducer
                            .produce(new RoqFrontMatterPaginatePageBuildItem(item.url(), item.source(), item.data(),
                                    null));
                } else {
                    // Publish page directly
                    pagesProducer
                            .produce(new RoqFrontMatterPublishNormalPageBuildItem(item.url(), item.source(), item.data(),
                                    null));
                }
            }

        }
    }

    // Walk the layout chain (page -> layout -> parent layout -> ...) and merge front matter.
    // Uses a stack so parent values are applied first, then child values override them.
    public static JsonObject mergeParents(RoqSiteConfig config, String layout, JsonObject data,
            String sourceAbsPath, Map<String, RoqFrontMatterRawLayoutBuildItem> byId) {
        Stack<JsonObject> fms = new Stack<>();
        String parent = layout;
        fms.add(data);
        while (parent != null) {
            if (!byId.containsKey(parent)) {
                final String layoutKey = getLayoutKey(config.theme(), parent);
                throw new RoqLayoutNotFoundException(
                        "Layout '%s' not found for file '%s'. Available layouts are: %s."
                                .formatted(layoutKey, sourceAbsPath,
                                        getAvailableLayouts(config, byId)));
            }
            final RoqFrontMatterRawLayoutBuildItem parentItem = byId.get(parent);
            parent = parentItem.layout();
            fms.push(parentItem.data());
        }

        JsonObject merged = new JsonObject();
        while (!fms.empty()) {
            merged.mergeIn(fms.pop());
        }
        return merged;
    }

    private static String getAvailableLayouts(RoqSiteConfig config, Map<String, RoqFrontMatterRawLayoutBuildItem> byId) {
        return byId.entrySet().stream()
                .filter(i -> !i.getValue().isThemeLayout())
                .map(i -> getLayoutKey(config.theme(), i.getKey()))
                .map("'%s'"::formatted).collect(Collectors.joining(", "));
    }

    static ZonedDateTime parsePublishDate(String path, JsonObject frontMatter, String dateFormat,
            ZoneId zoneId) {
        String dateString;
        final boolean fromFileName;
        if (frontMatter.containsKey(DATE_KEY)) {
            dateString = frontMatter.getString(DATE_KEY);
            fromFileName = false;
        } else {
            Matcher matcher = FILE_NAME_DATE_PATTERN.matcher(path);
            if (!matcher.find()) {
                // Lets fallback on using today's date if not specified
                return ZonedDateTime.now();
            }
            dateString = matcher.group(1);
            fromFileName = true;
        }
        try {
            return new DateTimeFormatterBuilder().appendPattern(dateFormat)
                    .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                    .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                    .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                    .toFormatter()
                    .withZone(zoneId)
                    .parse(dateString, ZonedDateTime::from);
        } catch (DateTimeParseException e) {
            if (fromFileName) {
                throw new RoqSiteScanningException(
                        "Error while reading date '%s' in file name: '%s'\nreason: %s".formatted(dateString, path,
                                e.getLocalizedMessage()));
            } else {
                throw new RoqFrontMatterReadingException(
                        "Error while reading FrontMatter 'date' ('%s') in file: '%s'\nreason: %s".formatted(dateString,
                                path,
                                e.getLocalizedMessage()));
            }
        }

    }

    // ── Debug printer (inner class) ──────────────────────────────────────

    static final class TemplateDebugPrinter {

        record DebugTemplateEntry(String path, String typeLabel,
                List<RoqFrontMatterAttachment> attachments) {
        }

        private TemplateDebugPrinter() {
        }

        static String buildTreeString(List<DebugTemplateEntry> entries) {
            TreeNode root = buildTree(entries);
            return buildString(root, "", "");
        }

        private static TreeNode buildTree(List<DebugTemplateEntry> entries) {
            Map<String, TreeNode> children = new TreeMap<>();

            for (DebugTemplateEntry item : entries) {
                String[] segments = item.path().split("/");
                children = insert(children, segments, 0, item.typeLabel(), item.attachments());
            }

            return new TreeNode("", null, Collections.unmodifiableMap(children));
        }

        private static Map<String, TreeNode> insert(Map<String, TreeNode> nodes, String[] segments, int index,
                String type, List<RoqFrontMatterAttachment> attachments) {
            Map<String, TreeNode> updated = new TreeMap<>(nodes);
            String name = segments[index];
            boolean isLeaf = index == segments.length - 1;

            if (isLeaf) {
                Map<String, TreeNode> attMap = new TreeMap<>();
                if (attachments != null) {
                    for (RoqFrontMatterAttachment att : attachments) {
                        attMap.put(att.name(),
                                new TreeNode(att.name(), "ATTACHMENT", Collections.emptyMap()));
                    }
                }
                updated.put(name, new TreeNode(name, type, Collections.unmodifiableMap(attMap)));
            } else {
                TreeNode child = nodes.getOrDefault(name, new TreeNode(name, null, new TreeMap<>()));
                Map<String, TreeNode> newChildren = insert(child.children(), segments, index + 1, type, attachments);
                updated.put(name, new TreeNode(name, null, newChildren));
            }

            return Collections.unmodifiableMap(updated);
        }

        private static final int NAME_COLUMN_WIDTH = 70;

        private static String buildString(TreeNode node, String indent, String prefix) {
            StringBuilder sb = new StringBuilder();

            if (!node.name().isEmpty()) {
                String typeLabel = node.type() != null ? node.type() : "";
                String namePart = indent + prefix + node.name();
                int spaces = Math.max(1, NAME_COLUMN_WIDTH - namePart.length());
                sb.append(namePart)
                        .append(" ".repeat(spaces))
                        .append(typeLabel)
                        .append("\n");

                indent += "    ";
            }

            List<TreeNode> sortedChildren = new ArrayList<>(node.children().values());
            sortedChildren.sort(Comparator
                    .comparingInt(TemplateDebugPrinter::priority)
                    .thenComparing(TreeNode::name, String.CASE_INSENSITIVE_ORDER));

            int size = sortedChildren.size();
            for (int i = 0; i < size; i++) {
                TreeNode child = sortedChildren.get(i);
                String childPrefix = (i == size - 1) ? "└── " : "├── ";
                sb.append(buildString(child, indent, childPrefix));
            }

            return sb.toString();
        }

        private static int priority(TreeNode node) {
            if ("layouts".equals(node.name) || "theme-layouts".equals(node.name)) {
                return 5;
            }

            if (node.type == null)
                return 3; // dir
            if (node.name().startsWith("index.")) {
                return 0;
            }
            return switch (node.type) {
                case "DOCUMENT_PAGE" -> 2;
                case "NORMAL_PAGE" -> 1;
                default -> 4;
            };
        }

        record TreeNode(
                String name,
                String type,
                Map<String, TreeNode> children) {
        }
    }
}
