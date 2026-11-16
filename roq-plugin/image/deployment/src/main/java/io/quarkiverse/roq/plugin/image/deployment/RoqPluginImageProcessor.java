package io.quarkiverse.roq.plugin.image.deployment;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jboss.logging.Logger;

import io.quarkiverse.qute.web.image.deployment.items.QuteImageExpressionBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageProcessedBuildItem;
import io.quarkiverse.qute.web.image.deployment.items.QuteImageToProcessBuildItem;
import io.quarkiverse.qute.web.image.runtime.ImageConfig;
import io.quarkiverse.qute.web.image.runtime.ImageUtils;
import io.quarkiverse.qute.web.image.runtime.model.GeneratedImage;
import io.quarkiverse.qute.web.image.runtime.model.ImageTag;
import io.quarkiverse.qute.web.image.spi.items.ImagesDirBuildItem;
import io.quarkiverse.qute.web.image.spi.items.QuteImageTemplateLocationBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.exception.RoqException;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterLayoutTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterPageTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterStaticFileBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterUnpublishedFileBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.record.RoqFrontMatterSiteModelBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkiverse.roq.plugin.image.runtime.RoqImageConfig;
import io.quarkiverse.roq.plugin.image.runtime.RoqImageException;
import io.quarkiverse.roq.plugin.image.runtime.RoqImageNamespaceResolver;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class RoqPluginImageProcessor {

    private static final Logger LOGGER = Logger.getLogger(RoqPluginImageProcessor.class);
    private static final String FEATURE = "roq-plugin-image";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(RoqImageNamespaceResolver.class));
    }

    /**
     * Literal absolute image paths resolve against the site's public and static directories, local and classpath:
     * {@code /images/photo.jpg} is {@code public/images/photo.jpg}, {@code /static/photo.png} is
     * {@code static/photo.png}. Page attachments and public images referenced through expressions
     * ({@code page.image}, {@code site.image}) are resolved by the build-time evaluation instead.
     */
    @BuildStep
    @SuppressWarnings("deprecation")
    void registerImageDirs(RoqSiteConfig config, RoqProjectBuildItem roqProject,
            BuildProducer<ImagesDirBuildItem> imageDirProducer) {
        if (!roqProject.isActive()) {
            return;
        }
        addLocalImageDir(imageDirProducer, roqProject, config.publicDir(), "");
        addLocalImageDir(imageDirProducer, roqProject, config.staticDir(), config.staticDir());
        imageDirProducer.produce(ImagesDirBuildItem.of(classpathDir(roqProject, config.publicDir())));
        imageDirProducer.produce(ImagesDirBuildItem.of(classpathDir(roqProject, config.staticDir()), config.staticDir()));
    }

    /**
     * Roq templates are generated under the build directory; relative literal image paths in a page or layout
     * resolve next to the real source instead. See the item's javadoc for why this is not
     * {@code TemplatePathBuildItem.source}.
     */
    @BuildStep
    @SuppressWarnings("deprecation")
    void declareTemplateLocations(RoqProjectBuildItem roqProject,
            List<RoqFrontMatterPageTemplateBuildItem> pages,
            List<RoqFrontMatterLayoutTemplateBuildItem> layouts,
            BuildProducer<QuteImageTemplateLocationBuildItem> locationProducer) {
        if (!roqProject.isActive()) {
            return;
        }
        for (RoqFrontMatterPageTemplateBuildItem page : pages) {
            declareTemplateLocation(locationProducer, roqProject, page.raw().templateSource());
        }
        for (RoqFrontMatterLayoutTemplateBuildItem layout : layouts) {
            declareTemplateLocation(locationProducer, roqProject, layout.raw().templateSource());
        }
    }

    /**
     * The source URI of a template: its file when the content is on disk, otherwise the classpath resource it was
     * read from (a {@code file:} URI under target/classes in dev and test, a {@code jar:} URI once packaged).
     */
    @SuppressWarnings("deprecation")
    private static void declareTemplateLocation(BuildProducer<QuteImageTemplateLocationBuildItem> producer,
            RoqProjectBuildItem roqProject, TemplateSource template) {
        SourceFile file = template.file();
        if (file == null || file.relativePath() == null) {
            return;
        }
        URI source = null;
        if (file.siteDirPath() != null && !file.siteDirPath().isBlank()) {
            Path local = Path.of(file.absolutePath());
            if (local.isAbsolute() && Files.isRegularFile(local)) {
                source = local.toUri();
            }
        }
        if (source == null) {
            String resource = roqProject.resolveRoqResourceSubDir(file.relativePath());
            URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
            if (url != null) {
                try {
                    source = url.toURI();
                } catch (URISyntaxException e) {
                    return;
                }
            }
        }
        if (source != null) {
            producer.produce(new QuteImageTemplateLocationBuildItem(template.generatedQuteTemplateId(), source));
        }
    }

    /**
     * Image usages whose source is an expression are reported by qute-web for every template that renders them,
     * layouts and tags included. The ones rendered by a page are evaluated here against that page ({@code page}
     * and {@code site} roots) or against every document (any other root, such as {@code post} in a tag), and
     * the files they resolve to are queued for conversion under the path the expression produces at render
     * time. The rest (usages reported for a layout or a tag itself) is rendered by nobody and skipped.
     */
    @BuildStep
    void evaluateImageExpressions(RoqProjectBuildItem roqProject, ImageConfig imageConfig,
            List<QuteImageExpressionBuildItem> expressions,
            Optional<RoqFrontMatterSiteModelBuildItem> siteModel,
            Optional<RoqFrontMatterRootUrlBuildItem> rootUrl,
            List<RoqFrontMatterStaticFileBuildItem> staticFiles,
            BuildProducer<QuteImageToProcessBuildItem> producer) {
        if (!roqProject.isActive() || siteModel.isEmpty() || rootUrl.isEmpty() || expressions.isEmpty()) {
            return;
        }
        final Site site = siteModel.get().site();
        final List<DocumentPage> documents = siteModel.get().documents();
        final Map<String, List<Page>> pagesByTemplateId = pagesByTemplateId(siteModel.get());
        final Map<String, RoqFrontMatterStaticFileBuildItem> filesByUrl = filesByUrl(rootUrl.get(), staticFiles);
        final ImageExpressionEvaluator evaluator = new ImageExpressionEvaluator();
        final Map<String, ImageExpressionEvaluator.Result> results = new HashMap<>();
        final Set<String> queued = new HashSet<>();
        int usages = 0;
        int contexts = 0;
        int noImage = 0;
        for (QuteImageExpressionBuildItem item : expressions) {
            List<Page> pages = pagesByTemplateId.get(item.templateId());
            if (pages == null) {
                continue;
            }
            usages++;
            if (imageConfig.preset(item.preset()).isEmpty()) {
                throw new RoqImageException(RoqException.builder("Unknown image preset")
                        .detail("'%s' is not a configured preset, used by '%s' in template '%s', %s.".formatted(
                                item.preset(), item.srcExpression(), item.templateId(), item.origin()))
                        .hint("Available presets (quarkus.qute.image.presets.*): %s"
                                .formatted(String.join(", ", imageConfig.presets().keySet())))
                        .sourceInfo(pages.get(0).source().template().file().toSourceInfo()));
            }
            String root = rootName(item.srcExpression());
            boolean pageRoot = root.equals("page") || root.equals("site");
            // a document root (post in a card) binds every document, whatever the page rendering it: zero
            // documents is zero contexts, not a failure
            List<? extends Page> targets = pageRoot ? pages : documents;
            // A usage failing in every context is a broken expression; one failing for some contexts only is a
            // page without that image.
            boolean resolved = false;
            String firstFailure = null;
            for (Page target : targets) {
                contexts++;
                Map<String, Object> data = pageRoot ? Map.of("page", target, "site", site)
                        : Map.of(root, target, "site", site);
                // the same document expression is reported once per page rendering it: evaluated once
                ImageExpressionEvaluator.Result result = results.computeIfAbsent(
                        item.srcExpression() + "|" + System.identityHashCode(target),
                        k -> evaluator.evaluate(item.srcExpression(), data));
                switch (result) {
                    case ImageExpressionEvaluator.Failed failed -> {
                        if (firstFailure == null) {
                            firstFailure = failed.message();
                        }
                    }
                    case ImageExpressionEvaluator.Error error -> {
                        // the model's own error, with the usage and the page it was evaluated for
                        RoqException cause = error.error();
                        throw new RoqImageException(RoqException.builder(cause.title())
                                .detail("%s Used by '%s' in template '%s', %s, evaluated for page '%s'.".formatted(
                                        cause.detail(), item.srcExpression(), item.templateId(), item.origin(),
                                        target.url()))
                                .hint(cause.hint())
                                .sourceInfo(target.source().template().file().toSourceInfo())
                                .cause(cause));
                    }
                    case ImageExpressionEvaluator.Empty empty -> {
                        resolved = true;
                        noImage++;
                    }
                    case ImageExpressionEvaluator.Value value -> {
                        resolved = true;
                        if (ImageUtils.isRemote(value.value())) {
                            // qute-web downloads it, see quarkus.qute.image.remote
                            if (queued.add(value.value() + "|" + item.preset())) {
                                producer.produce(QuteImageToProcessBuildItem.remote(value.value(), item.preset()));
                            }
                            continue;
                        }
                        if (RoqUrl.isFullPath(value.value())) {
                            continue;
                        }
                        RoqFrontMatterStaticFileBuildItem file = filesByUrl.get(value.value());
                        if (file == null) {
                            throw new RoqImageException(RoqException.builder("Image not found")
                                    .detail("'%s' resolves to '%s' in template '%s', %s, but the site has no such file."
                                            .formatted(item.srcExpression(), value.value(), item.templateId(),
                                                    item.origin()))
                                    .hint("Check the image name, it must be a page attachment or a public file."));
                        }
                        if (queued.add(value.value() + "|" + item.preset())) {
                            producer.produce(file.isFile()
                                    ? QuteImageToProcessBuildItem.of(value.value(), item.preset(), file.filePath())
                                    : QuteImageToProcessBuildItem.of(value.value(), item.preset(),
                                            fileName(file.link()), file.content()));
                        }
                    }
                }
            }
            if (!resolved && firstFailure != null) {
                if (pageRoot) {
                    throw new RoqImageException(RoqException.builder("Unresolvable image expression")
                            .detail("'%s' (preset %s) in template '%s', %s: %s".formatted(item.srcExpression(),
                                    item.preset(), item.templateId(), item.origin(), firstFailure))
                            .hint("Use a literal path, or a page/site image expression the build can evaluate."));
                }
                // not a document either (a data loop, for instance): the render-time lookup takes over
                LOGGER.warnf("Image expression '%s' in template '%s', %s, is not a page, site or document expression"
                        + " (%s). Its images are not converted at build time unless a quarkus.qute.image.generate"
                        + " glob covers them.", item.srcExpression(), item.templateId(), item.origin(), firstFailure);
            }
        }
        LOGGER.infof("Evaluated %d image expression usages in %d contexts: %d images queued, %d without image",
                usages, contexts, queued.size(), noImage);
    }

    /**
     * The generated variants are selected for the static site, whatever the configured paths. Unless
     * {@code publish-originals} is on, the originals that were converted are kept out of the site. The original of a
     * usage is the static file its declared path points to: the url the host passed, an absolute path from the
     * site root, or a name next to the page the template belongs to. Sources used as they are (vectors, formats the
     * converter cannot read) have no variants and stay published.
     */
    @BuildStep
    void selectGeneratedImages(RoqProjectBuildItem roqProject, RoqImageConfig config,
            Optional<QuteImageProcessedBuildItem> processed,
            Optional<RoqFrontMatterSiteModelBuildItem> siteModel,
            Optional<RoqFrontMatterRootUrlBuildItem> rootUrl,
            List<RoqFrontMatterStaticFileBuildItem> staticFiles,
            BuildProducer<SelectedPathBuildItem> selectedPathProducer,
            BuildProducer<RoqFrontMatterUnpublishedFileBuildItem> unpublishedProducer) {
        if (!roqProject.isActive() || processed.isEmpty()) {
            return;
        }
        final Set<String> selected = new HashSet<>();
        for (ImageTag tag : processed.get().tags()) {
            for (GeneratedImage generated : tag.image().generated()) {
                if (selected.add(generated.outputPath())) {
                    selectedPathProducer.produce(new SelectedPathBuildItem(generated.outputPath(), null));
                }
            }
        }
        if (config.publishOriginals() || siteModel.isEmpty() || rootUrl.isEmpty()) {
            return;
        }
        final Map<String, List<Page>> pagesByTemplateId = pagesByTemplateId(siteModel.get());
        final Map<String, RoqFrontMatterStaticFileBuildItem> filesByUrl = filesByUrl(rootUrl.get(), staticFiles);
        final Set<String> unpublished = new HashSet<>();
        for (ImageTag tag : processed.get().tags()) {
            if (tag.image().passThrough() || tag.image().generated().isEmpty()) {
                continue;
            }
            RoqFrontMatterStaticFileBuildItem original = originalOf(tag, rootUrl.get(), pagesByTemplateId, filesByUrl);
            if (original != null && unpublished.add(original.link())) {
                unpublishedProducer.produce(new RoqFrontMatterUnpublishedFileBuildItem(original.link()));
            }
        }
        LOGGER.debugf("Selected %d generated images, unpublished %d converted originals", selected.size(),
                unpublished.size());
    }

    private static RoqFrontMatterStaticFileBuildItem originalOf(ImageTag tag, RoqFrontMatterRootUrlBuildItem rootUrl,
            Map<String, List<Page>> pagesByTemplateId, Map<String, RoqFrontMatterStaticFileBuildItem> filesByUrl) {
        String declared = tag.declaredPath();
        if (ImageUtils.isRemote(declared)) {
            // downloaded by qute-web: there is no original in the site
            return null;
        }
        if (declared.startsWith("/")) {
            // a url the host passed (root path included), else an absolute path from the site root
            RoqFrontMatterStaticFileBuildItem file = filesByUrl.get(declared);
            return file != null ? file : filesByUrl.get(RoqUrl.fromRoot(rootUrl.rootUrl(), declared).toString());
        }
        List<Page> pages = pagesByTemplateId.get(tag.templateId());
        if (pages == null || pages.isEmpty()) {
            return null;
        }
        return filesByUrl.get(pages.get(0).url().resolveAttachment(declared).toString());
    }

    private static Map<String, List<Page>> pagesByTemplateId(RoqFrontMatterSiteModelBuildItem siteModel) {
        Map<String, List<Page>> pagesByTemplateId = new HashMap<>();
        for (Page page : siteModel.allPages()) {
            pagesByTemplateId.computeIfAbsent(page.source().template().generatedQuteTemplateId(), k -> new ArrayList<>())
                    .add(page);
        }
        return pagesByTemplateId;
    }

    private static Map<String, RoqFrontMatterStaticFileBuildItem> filesByUrl(RoqFrontMatterRootUrlBuildItem rootUrl,
            List<RoqFrontMatterStaticFileBuildItem> staticFiles) {
        Map<String, RoqFrontMatterStaticFileBuildItem> filesByUrl = new HashMap<>();
        for (RoqFrontMatterStaticFileBuildItem file : staticFiles) {
            filesByUrl.put(RoqUrl.fromRoot(rootUrl.rootUrl(), file.link()).toString(), file);
        }
        return filesByUrl;
    }

    private static String fileName(String link) {
        return link.substring(link.lastIndexOf('/') + 1);
    }

    private static String rootName(String expression) {
        int end = expression.length();
        for (char c : new char[] { '.', '(', '[' }) {
            int i = expression.indexOf(c);
            if (i >= 0 && i < end) {
                end = i;
            }
        }
        return expression.substring(0, end);
    }

    private static String classpathDir(RoqProjectBuildItem roqProject, String subDir) {
        return roqProject.isRoqResourcesInRoot() ? subDir : roqProject.resolveRoqResourceSubDir(subDir);
    }

    private static void addLocalImageDir(BuildProducer<ImagesDirBuildItem> producer,
            RoqProjectBuildItem roqProject, String subDir, String urlPrefix) {
        Path localDir = roqProject.fromLocalRoqDir(subDir);
        if (localDir != null && Files.isDirectory(localDir)) {
            producer.produce(ImagesDirBuildItem.of(urlPrefix, localDir));
        }
    }
}
