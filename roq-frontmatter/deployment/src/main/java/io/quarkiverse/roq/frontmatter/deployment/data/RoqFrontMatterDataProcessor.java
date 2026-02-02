package io.quarkiverse.roq.frontmatter.deployment.data;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontmatterTemplateUtils.getLayoutKey;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.TemplateLink;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqFrontMatterReadingException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqLayoutNotFoundException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqSiteScanningException;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishNormalPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem.Attachment;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.TemplateDebugPrinter;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterDataProcessor {
    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqFrontMatterDataProcessor.class);
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
            List<RoqFrontMatterRawTemplateBuildItem> rawTemplates) {
        if (rawTemplates.isEmpty()) {
            return;
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Template definition: \n\n" + TemplateDebugPrinter.buildTreeString(rawTemplates));
        }

        final var layoutsById = rawTemplates.stream()
                .filter(i -> !i.type().isPage())
                .collect(Collectors.toMap(RoqFrontMatterRawTemplateBuildItem::id, Function.identity(), (a, b) -> {
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
        for (RoqFrontMatterRawTemplateBuildItem item : rawTemplates) {
            JsonObject data = mergeParents(config, item, layoutsById);
            RoqUrl url = null;
            if (item.isPage()) {
                // Parse date
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
                        ? new PageFiles(item.attachments().stream().map(Attachment::name).toList(), config.slugifyFiles())
                        : null;
                final PageSource pageSource = new PageSource(item.templateSource(), draft, dateString, files, false);
                final String link = TemplateLink.pageLink(config.pathPrefixOrEmpty(),
                        data.getString(LINK_KEY),
                        new TemplateLink.PageLinkData(pageSource, item.collectionId(), data));
                url = rootUrl.resolve(link);

                pageTemplatesProducer.produce(new RoqFrontMatterPageTemplateBuildItem(item, data, pageSource, url));
            } else {
                RoqFrontMatterLayoutTemplateBuildItem templateItem = new RoqFrontMatterLayoutTemplateBuildItem(item, data);
                layoutTemplateProducer.produce(templateItem);
            }
        }
    }

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
                for (Attachment attachment : item.raw().attachments()) {
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

    public static JsonObject mergeParents(RoqSiteConfig config, RoqFrontMatterRawTemplateBuildItem item,
            Map<String, RoqFrontMatterRawTemplateBuildItem> byId) {
        Stack<JsonObject> fms = new Stack<>();
        String parent = item.layout();
        fms.add(item.data());
        while (parent != null) {
            if (!byId.containsKey(parent)) {
                final String layoutKey = getLayoutKey(config.theme(), parent);
                throw new RoqLayoutNotFoundException(
                        "Layout '%s' not found for file '%s'. Available layouts are: %s."
                                .formatted(layoutKey, item.templateSource().file().absolutePath(),
                                        getAvailableLayouts(config, byId)));
            }
            final RoqFrontMatterRawTemplateBuildItem parentItem = byId.get(parent);
            parent = parentItem.layout();
            fms.push(parentItem.data());
        }

        JsonObject merged = new JsonObject();
        while (!fms.empty()) {
            merged.mergeIn(fms.pop());
        }
        return merged;
    }

    private static String getAvailableLayouts(RoqSiteConfig config, Map<String, RoqFrontMatterRawTemplateBuildItem> byId) {
        return byId.entrySet().stream()
                .filter(i -> i.getValue().isLayout())
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

}
