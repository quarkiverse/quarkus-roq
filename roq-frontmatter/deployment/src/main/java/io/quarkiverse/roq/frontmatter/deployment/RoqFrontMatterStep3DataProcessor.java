package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterConstants.FILE_NAME_DATE_PATTERN;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterLayoutUtils.getLayoutKey;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.DATE;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.DRAFT;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.LINK;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.PAGINATE;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;
import java.util.regex.Matcher;
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
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishNormalPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.util.TemplateLink;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.exception.RoqException;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterStep3DataProcessor {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqFrontMatterStep3DataProcessor.class);

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
                    item.templateSource(), layoutsById);
            layoutTemplateProducer.produce(new RoqFrontMatterLayoutTemplateBuildItem(item, data));
        }

        // Process pages: merge parent data, then filter by date/draft rules
        for (RoqFrontMatterRawPageBuildItem item : rawPages) {
            JsonObject data = mergeParents(config, item.layout(), item.data(),
                    item.templateSource(), layoutsById);

            // Parse date from front matter "date" key, or from filename pattern (e.g. 2024-03-10-my-post)
            ZonedDateTime date = parsePublishDate(item.templateSource().path(), data, config.dateFormat(),
                    config.timeZoneOrDefault(), item.templateSource());

            // Collection documents (posts, etc.) always need a date since templates use {post.date.format(...)}.
            // Normal pages don't need one — null is fine.
            if (date == null && item.collection() != null) {
                date = ZonedDateTime.now(config.timeZoneOrDefault());
            }

            final boolean noFuture = !config.future() && (item.collection() == null || !item.collection().future());
            if (noFuture && date != null && date.isAfter(ZonedDateTime.now())) {
                LOGGER.warnf("Ignoring page '%s' because it's scheduled for later (%s > %s)." +
                        " To display future articles, use -Dsite.future=true%s.", item.templateSource().path(), date,
                        ZonedDateTime.now(),
                        item.collection() == null ? ""
                                : " or -Dsite.collections.%s.future=true".formatted(item.collection().id()));
                continue;
            }

            String dateString = date != null ? date.format(DateTimeFormatter.ISO_ZONED_DATE_TIME) : null;
            final boolean draft = Boolean.parseBoolean(data.getString(DRAFT, "false"));
            if (!config.draft() && draft) {
                continue;
            }
            final PageFiles files = item.attachments() != null
                    ? new PageFiles(item.attachments().stream().map(RoqFrontMatterAttachment::name).toList(),
                            config.slugifyFiles())
                    : null;
            final PageSource pageSource = new PageSource(item.templateSource(), draft, dateString, files, false);
            final String link = TemplateLink.pageLink(config.pathPrefixOrEmpty(),
                    data.getString(LINK),
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
                Object paginate = item.data().getValue(PAGINATE);
                if (paginate != null && !Boolean.FALSE.equals(paginate)) {
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
            TemplateSource source, Map<String, RoqFrontMatterRawLayoutBuildItem> byId) {
        Stack<JsonObject> fms = new Stack<>();
        Set<String> visited = new HashSet<>();
        String parent = layout;
        fms.add(data);
        while (parent != null) {
            if (!visited.add(parent)) {
                throw new RuntimeException(
                        "Circular layout reference detected for file '%s': layout '%s' forms a cycle."
                                .formatted(source.file().relativePath(), parent));
            }
            if (!byId.containsKey(parent)) {
                final String layoutKey = getLayoutKey(config.theme(), parent);
                throw new RoqLayoutNotFoundException(
                        RoqException.builder("Layout not found")
                                .source(source)
                                .detail("Layout '%s' could not be resolved.".formatted(layoutKey))
                                .hint("Available layouts: %s".formatted(getAvailableLayouts(config, byId))));
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
        String local = byId.entrySet().stream()
                .filter(i -> !i.getValue().isThemeLayout())
                .map(i -> getLayoutKey(config.theme(), i.getKey()))
                .map("'%s'"::formatted).collect(Collectors.joining(", "));
        String theme = byId.entrySet().stream()
                .filter(i -> i.getValue().isThemeLayout())
                .map(i -> getLayoutKey(config.theme(), i.getKey()))
                .map("'%s'"::formatted).collect(Collectors.joining(", "));
        if (theme.isEmpty()) {
            return local;
        }
        return local + ". Theme layouts: " + theme;
    }

    static ZonedDateTime parsePublishDate(String path, JsonObject frontMatter, String dateFormat,
            ZoneId zoneId, TemplateSource source) {
        String dateString;
        final boolean fromFileName;
        if (frontMatter.containsKey(DATE) && frontMatter.getValue(DATE) != null) {
            dateString = String.valueOf(frontMatter.getValue(DATE));
            fromFileName = false;
        } else {
            Matcher matcher = FILE_NAME_DATE_PATTERN.matcher(path);
            if (!matcher.find()) {
                // No date in frontmatter or filename — caller decides the fallback.
                return null;
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
                        RoqException.builder("Invalid date in file name")
                                .source(source)
                                .detail("Could not parse date '%s' from the file name.".formatted(dateString))
                                .hint("Rename the file so the date matches the configured format: '%s'.".formatted(dateFormat))
                                .cause(e));
            } else {
                throw new RoqFrontMatterReadingException(
                        RoqException.builder("Invalid date format")
                                .source(source)
                                .detail("Could not parse front matter 'date' value '%s'.".formatted(dateString))
                                .hint("Use a date string matching the configured format: '%s'.".formatted(dateFormat))
                                .cause(e));
            }
        }

    }

}
