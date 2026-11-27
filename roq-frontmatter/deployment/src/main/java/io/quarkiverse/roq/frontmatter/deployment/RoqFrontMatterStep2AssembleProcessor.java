package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterAssembleUtils.processTemplate;
import static io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterLayoutUtils.getIncludeFilter;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.LANG;
import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.TRANSLATION_OF;
import static io.quarkiverse.tools.stringpaths.StringPaths.slugify;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.data.deployment.exception.DataConversionException;
import io.quarkiverse.roq.data.deployment.items.DataMappingBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.exception.RoqException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqFrontMatterReadingException;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterLanguageVariant;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawLayoutBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDataModificationBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterAvailableLayoutsBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterScannedContentBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.scan.RoqFrontMatterScannedLayoutBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterAssembleUtils.ProcessedTemplate;
import io.quarkiverse.roq.frontmatter.deployment.util.RoqFrontMatterLanguageUtils;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqFrontMatterConfigException;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RoqFrontMatterStep2AssembleProcessor {

    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterStep2AssembleProcessor.class);

    // ── Available layouts index ─────────────────────────────────────────────

    @BuildStep
    void buildAvailableLayouts(List<RoqFrontMatterScannedLayoutBuildItem> scannedLayouts,
            BuildProducer<RoqFrontMatterAvailableLayoutsBuildItem> producer) {
        Map<String, SourceFile> layoutsById = new LinkedHashMap<>();
        for (RoqFrontMatterScannedLayoutBuildItem scanned : scannedLayouts) {
            String id = scanned.metadata().templateId();
            SourceFile existing = layoutsById.put(id, scanned.metadata().sourceFile());
            if (existing != null) {
                throw new IllegalStateException(
                        """

                                Multiple layouts found with id '%s'.
                                 - '%s'
                                 - '%s'
                                This usually happens when more than one 'layouts' directory provides a template with the same id. Please ensure layout IDs are unique across all themes and sources.
                                """
                                .formatted(id, existing.absolutePath(),
                                        scanned.metadata().sourceFile().absolutePath()));
            }
        }
        producer.produce(new RoqFrontMatterAvailableLayoutsBuildItem(layoutsById));
    }

    // ── Content processing ───────────────────────────────────────────────

    @BuildStep
    void processContent(RoqSiteConfig config,
            RoqFrontMatterAvailableLayoutsBuildItem availableLayouts,
            List<RoqFrontMatterScannedContentBuildItem> scannedContent,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            BuildProducer<RoqFrontMatterRawPageBuildItem> rawPageProducer) {
        if (scannedContent.isEmpty()) {
            return;
        }

        // Data modifications (e.g. escape rules, draft marking) are applied during processing
        final List<RoqFrontMatterDataModificationBuildItem> sortedModifications = dataModifications.stream()
                .sorted(Comparator.comparing(RoqFrontMatterDataModificationBuildItem::order))
                .toList();

        // Multilingual: group the language variants of each article folder (empty when the feature is off)
        final Map<RoqFrontMatterScannedContentBuildItem, RoqFrontMatterLanguageVariant> variants = languageVariants(
                config, scannedContent);

        // processTemplate resolves layout, applies data modifications, and generates
        // the Qute template (with layout include and markup wrapping)
        // Default variants are processed first: a translation inherits the layout of its default variant (Ruling 16)
        final Map<RoqFrontMatterScannedContentBuildItem, ProcessedTemplate> processedByScanned = new HashMap<>();
        final Map<String, JsonObject> defaultDataByGroup = new HashMap<>();
        for (RoqFrontMatterScannedContentBuildItem scanned : scannedContent) {
            final RoqFrontMatterLanguageVariant variant = variants.get(scanned);
            if (variant == null || variant.isDefault()) {
                ProcessedTemplate processed = processTemplate(
                        scanned.metadata(), true, false, scanned.collection(),
                        scanned.isIndex(), scanned.isSiteIndex(),
                        config, availableLayouts, sortedModifications, null);
                processedByScanned.put(scanned, processed);
                if (variant != null) {
                    defaultDataByGroup.put(variant.group(), processed.data());
                }
            }
        }
        for (RoqFrontMatterScannedContentBuildItem scanned : scannedContent) {
            final RoqFrontMatterLanguageVariant variant = variants.get(scanned);
            if (variant != null && !variant.isDefault()) {
                // A translation is not a collection member: only the default-language variant is listed
                processedByScanned.put(scanned, processTemplate(
                        scanned.metadata(), true, false, null,
                        scanned.isIndex(), scanned.isSiteIndex(),
                        config, availableLayouts, sortedModifications,
                        defaultDataByGroup.get(variant.group())));
            }
        }

        for (RoqFrontMatterScannedContentBuildItem scanned : scannedContent) {
            ProcessedTemplate processed = processedByScanned.get(scanned);

            final RoqFrontMatterLanguageVariant variant = variants.get(scanned);
            // Every content (page, document, translation) has a language: the default locale unless set otherwise
            if (!processed.data().containsKey(LANG)) {
                processed.data().put(LANG, config.defaultLocale());
            }
            if (variant != null) {
                // The suffix sets the language of a translation; the default variant keeps a frontmatter lang if any.
                // The URL prefix and the translations list need the computed URLs: they are set in Step 3.
                if (!variant.isDefault() || !processed.data().containsKey(LANG)) {
                    processed.data().put(LANG, variant.lang());
                }
                // Records the group of a translation (its front matter value is kept when set explicitly)
                if (!variant.isDefault()) {
                    processed.data().put(TRANSLATION_OF, variant.group());
                }
            }

            LOGGER.debugf("Roq content processing producing raw page '%s'", processed.id());
            final boolean translation = variant != null && !variant.isDefault();
            rawPageProducer.produce(new RoqFrontMatterRawPageBuildItem(
                    processed.templateSource(), processed.layout(), processed.data(),
                    translation ? null : scanned.collection(), scanned.metadata().parserConfig(),
                    processed.generatedTemplate(),
                    scanned.attachments(), variant));
        }
    }

    /**
     * Groups the multilingual variants. A page joins a group in one of two ways:
     * <ul>
     * <li>its front matter {@code translation-of} names the group. Pages sharing the value form one group, whatever
     * their file name or folder. An explicit value wins over the folder of an index file;</li>
     * <li>without {@code translation-of}, an index file ({@code index.<ext>} or {@code index.<lang>.<ext>}) joins the
     * group of its folder.</li>
     * </ul>
     * The default variant is the page whose language is {@code site.default-locale}. Its language is its file suffix,
     * else its front matter {@code lang}, else the default locale. A group without translation is not a group.
     *
     * @return the variant of each grouped scanned item; empty when the site has no translation
     */
    private static Map<RoqFrontMatterScannedContentBuildItem, RoqFrontMatterLanguageVariant> languageVariants(
            RoqSiteConfig config, List<RoqFrontMatterScannedContentBuildItem> scannedContent) {
        final String defaultLocale = config.defaultLocale();
        final Set<String> languages = RoqFrontMatterLanguageUtils.translationLanguages(defaultLocale);
        final Map<String, Map<String, RoqFrontMatterScannedContentBuildItem>> groups = new LinkedHashMap<>();
        for (RoqFrontMatterScannedContentBuildItem scanned : scannedContent) {
            if (scanned.isSiteIndex()) {
                continue;
            }
            final Path path = Path.of(scanned.metadata().referencePath());
            final String fileName = path.getFileName().toString();
            final JsonObject data = scanned.metadata().parsedHeaders().data();
            final Optional<RoqFrontMatterLanguageUtils.LanguageSuffix> suffix = RoqFrontMatterLanguageUtils.parse(fileName,
                    languages);
            final String group;
            final String lang;
            if (data.containsKey(TRANSLATION_OF)) {
                final Object explicit = data.getValue(TRANSLATION_OF);
                if (!(explicit instanceof String name) || name.isBlank()) {
                    throw languageError(
                            "Frontmatter key '%s' of '%s' must be a non-empty string".formatted(TRANSLATION_OF,
                                    scanned.metadata().referencePath()),
                            "'translation-of' names the translation group the page belongs to.",
                            "Set 'translation-of' to a name shared by the pages of the same article.");
                }
                group = name;
                final String fmLang = data.containsKey(LANG) ? String.valueOf(data.getValue(LANG)) : null;
                lang = suffix.map(RoqFrontMatterLanguageUtils.LanguageSuffix::lang).orElse(
                        fmLang != null ? fmLang : defaultLocale);
            } else if (suffix.isPresent() && path.getParent() != null) {
                group = toUnixPath(path.getParent().toString());
                lang = suffix.get().lang() != null ? suffix.get().lang() : defaultLocale;
            } else {
                continue;
            }
            final Map<String, RoqFrontMatterScannedContentBuildItem> members = groups.computeIfAbsent(group,
                    k -> new LinkedHashMap<>());
            final RoqFrontMatterScannedContentBuildItem previous = members.putIfAbsent(lang, scanned);
            if (previous != null) {
                throw languageError(
                        "Duplicate language '%s' in translation group '%s' (duplicate language variant)"
                                .formatted(lang, group),
                        "Both '%s' and '%s' are '%s' variants of the group."
                                .formatted(previous.metadata().referencePath(),
                                        scanned.metadata().referencePath(), lang),
                        "Keep a single page per language in each translation group.");
            }
        }

        final Set<String> topDirs = new HashSet<>();
        for (RoqFrontMatterScannedContentBuildItem scanned : scannedContent) {
            final Path sp = Path.of(scanned.metadata().referencePath());
            if (sp.getNameCount() > 1) {
                topDirs.add(sp.getName(0).toString());
            }
        }

        final Map<RoqFrontMatterScannedContentBuildItem, RoqFrontMatterLanguageVariant> result = new HashMap<>();
        for (Map.Entry<String, Map<String, RoqFrontMatterScannedContentBuildItem>> entry : groups.entrySet()) {
            final String group = entry.getKey();
            final Map<String, RoqFrontMatterScannedContentBuildItem> members = entry.getValue();
            final RoqFrontMatterScannedContentBuildItem defaultVariant = members.get(defaultLocale);
            final List<String> translationLangs = members.keySet().stream().filter(lang -> !lang.equals(defaultLocale))
                    .toList();
            if (translationLangs.isEmpty()) {
                continue;
            }
            if (defaultVariant == null) {
                throw languageError(
                        "Translation group '%s' is missing its default-language variant".formatted(group),
                        "A translated article needs a page in the default language ('%s') in the same group."
                                .formatted(defaultLocale),
                        "Add a page with the default language, or remove the translations of '%s'.".formatted(group));
            }
            for (String lang : translationLangs) {
                if (!languages.contains(lang)) {
                    throw languageError(
                            "Language '%s' of translation group '%s' is not a supported translation language"
                                    .formatted(lang, group),
                            "The language comes from the file suffix or the front matter 'lang'.",
                            "Use a language code of the built-in language catalogue.");
                }
                if (topDirs.contains(lang)) {
                    throw languageError(
                            "Language '%s' collides with the top-level content directory '%s' (language/directory collision)"
                                    .formatted(lang, lang),
                            "Translations are published under '/%s/', which would collide with the content directory '%s/'."
                                    .formatted(lang, lang),
                            "Rename the directory '%s/' or the '%s' translations.".formatted(lang, lang));
                }
            }
            result.put(defaultVariant, new RoqFrontMatterLanguageVariant(group, defaultLocale, true));
            members.forEach((lang, translation) -> {
                if (!lang.equals(defaultLocale)) {
                    result.put(translation, new RoqFrontMatterLanguageVariant(group, lang, false));
                }
            });
        }
        return result;
    }

    private static RoqFrontMatterConfigException languageError(String title, String detail, String hint) {
        return new RoqFrontMatterConfigException(RoqException.builder(title).detail(detail).hint(hint));
    }

    // ── Layout processing ────────────────────────────────────────────────

    @BuildStep
    void processLayouts(RoqSiteConfig config,
            RoqFrontMatterAvailableLayoutsBuildItem availableLayouts,
            List<RoqFrontMatterScannedLayoutBuildItem> scannedLayouts,
            List<RoqFrontMatterDataModificationBuildItem> dataModifications,
            BuildProducer<RoqFrontMatterRawLayoutBuildItem> rawLayoutProducer) {
        if (scannedLayouts.isEmpty()) {
            return;
        }

        final List<RoqFrontMatterDataModificationBuildItem> sortedModifications = dataModifications.stream()
                .sorted(Comparator.comparing(RoqFrontMatterDataModificationBuildItem::order))
                .toList();

        for (RoqFrontMatterScannedLayoutBuildItem scanned : scannedLayouts) {
            boolean isThemeLayout = scanned.isThemeLayout();
            ProcessedTemplate processed = processTemplate(
                    scanned.metadata(), false, isThemeLayout, null,
                    false, false,
                    config, availableLayouts, sortedModifications);

            LOGGER.debugf("Roq layout processing producing raw layout '%s'", processed.id());
            rawLayoutProducer.produce(new RoqFrontMatterRawLayoutBuildItem(
                    processed.templateSource(), processed.layout(), processed.data(),
                    scanned.metadata().parserConfig(),
                    processed.generatedTemplate(), isThemeLayout));
        }
    }

    // ── Roq Data processing ───────────────────────────────────────────────

    @BuildStep
    void processDataContent(RoqProjectBuildItem roqProject,
            RoqSiteConfig siteConfig,
            RoqFrontMatterAvailableLayoutsBuildItem availableLayouts,
            List<RoqDataJsonBuildItem> roqDataJsonBuildItems,
            List<DataMappingBuildItem> roqDataBeanBuildItems,
            BuildProducer<RoqFrontMatterRawPageBuildItem> rawPageProducer) {

        Map<String, Supplier<Object>> dataBeans = new HashMap<>(roqDataBeanBuildItems.stream()
                .collect(Collectors.toMap(DataMappingBuildItem::getName, item -> () -> this.convert(item))));
        dataBeans.putAll(roqDataJsonBuildItems.stream()
                .collect(Collectors.toMap(RoqDataJsonBuildItem::getName, item -> item::getData)));

        siteConfig.collections().forEach(collection -> collection.fromData().ifPresent(ignored -> {
            Supplier<Object> dataSupplier = dataBeans.get(collection.dataName());
            if (dataSupplier == null) {
                throw new RoqFrontMatterReadingException(
                        RoqException.builder("No data source found for collection '%s'".formatted(collection.id()))
                                .hint("Add a '%s.yaml' (or .json) data file, or a '%s/' data directory"
                                        .formatted(collection.dataName(), collection.dataName())));
            }
            generateDataPages(roqProject, collection, dataSupplier.get(), rawPageProducer, availableLayouts, siteConfig);
        }));

    }

    private void generateDataPages(RoqProjectBuildItem roqProject, ConfiguredCollection configuredCollection, Object item,
            BuildProducer<RoqFrontMatterRawPageBuildItem> rawPageProducer,
            RoqFrontMatterAvailableLayoutsBuildItem availableLayouts, RoqSiteConfig config) {
        switch (item) {
            case JsonObject jsonObject -> {
                if (jsonObject.containsKey(configuredCollection.idKey())) {
                    // Single item with the id key at top level
                    generateDataPage(configuredCollection, jsonObject, rawPageProducer, roqProject, availableLayouts, config);
                } else {
                    // Map of items (e.g. from a data directory), iterate values
                    for (Map.Entry<String, Object> entry : jsonObject) {
                        if (!(entry.getValue() instanceof JsonObject entryObject)) {
                            throw new RoqFrontMatterReadingException(
                                    RoqException.builder("Invalid data entry '%s' in collection '%s'"
                                            .formatted(entry.getKey(), configuredCollection.id()))
                                            .detail("Expected a JSON object but got: %s".formatted(
                                                    entry.getValue() == null ? "null"
                                                            : entry.getValue().getClass().getSimpleName()))
                                            .hint("Each entry in the data must be a JSON object with an '%s' key"
                                                    .formatted(configuredCollection.idKey())));
                        }
                        if ("_key".equals(configuredCollection.idKey())
                                && !entryObject.containsKey("_key")) {
                            entryObject.put("_key", entry.getKey());
                        }
                        if (!entryObject.containsKey(configuredCollection.idKey())) {
                            throw new RoqFrontMatterReadingException(
                                    RoqException.builder("Missing id-key '%s' in data entry '%s' for collection '%s'"
                                            .formatted(configuredCollection.idKey(), entry.getKey(),
                                                    configuredCollection.id()))
                                            .hint("Each data entry must contain the '%s' field configured as id-key"
                                                    .formatted(configuredCollection.idKey())));
                        }
                        generateDataPage(configuredCollection, entryObject, rawPageProducer, roqProject, availableLayouts,
                                config);
                    }
                }
            }
            case JsonArray jsonArray -> jsonArray.forEach(element -> {
                if (element instanceof JsonObject jsonObject) {
                    generateDataPage(configuredCollection, jsonObject, rawPageProducer, roqProject, availableLayouts, config);
                } else {
                    throw new RoqFrontMatterReadingException(
                            RoqException.builder("Invalid data element in collection '%s'"
                                    .formatted(configuredCollection.id()))
                                    .detail("Expected a JSON object but got: %s".formatted(
                                            element == null ? "null" : element.getClass().getSimpleName()))
                                    .hint("Each element in the data array must be a JSON object with an '%s' key"
                                            .formatted(configuredCollection.idKey())));
                }
            });
            default -> throw new RoqFrontMatterReadingException(
                    RoqException.builder("Unsupported data type for collection '%s'".formatted(configuredCollection.id()))
                            .detail("Expected a JSON object or array but got: %s".formatted(item.getClass().getSimpleName()))
                            .hint("The data file should contain either a single JSON object or an array of objects"));
        }
    }

    private void generateDataPage(ConfiguredCollection configuredCollection, JsonObject item,
            BuildProducer<RoqFrontMatterRawPageBuildItem> rawPagesProducer, RoqProjectBuildItem roqProject,
            RoqFrontMatterAvailableLayoutsBuildItem availableLayouts, RoqSiteConfig config) {
        final String extractedKey = item.getString(configuredCollection.idKey());
        if (extractedKey == null) {
            throw new RoqFrontMatterReadingException(RoqException
                    .builder("Error extracting title value from %s collection values".formatted(configuredCollection.id()))
                    .hint("Extracted key for %s is null with property key %s".formatted(item.toString(),
                            configuredCollection.idKey())));
        }
        final String id = configuredCollection.id() + "/" + slugify(extractedKey, false, false);
        final String path = id + ".html";
        final String layoutId = availableLayouts.resolveCollectionLayoutId(config.theme(), configuredCollection);
        rawPagesProducer.produce(new RoqFrontMatterRawPageBuildItem(
                TemplateSource.create(
                        id,
                        null,
                        new SourceFile(
                                toUnixPath(roqProject.local().roqDir().normalize().toAbsolutePath()
                                        .toString()),
                                path),
                        path,
                        id + ".html",
                        false,
                        true,
                        false,
                        false),
                layoutId,
                item,
                configuredCollection,
                getIncludeFilter(layoutId, true).apply(""),
                List.of()));
    }

    private Object convert(DataMappingBuildItem dataMappingBuildItem) {
        try {
            return dataMappingBuildItem.getConverter().convert(dataMappingBuildItem.getContent());
        } catch (IOException e) {
            throw new DataConversionException(
                    RoqException.builder("Unable to convert data file")
                            .detail("Could not convert file %s as an Object"
                                    .formatted(dataMappingBuildItem.sourceFile()))
                            .sourceFilePath(dataMappingBuildItem.sourceFile().toString())
                            .hint("Verify the file contains valid YAML or JSON")
                            .cause(e));
        }
    }
}
