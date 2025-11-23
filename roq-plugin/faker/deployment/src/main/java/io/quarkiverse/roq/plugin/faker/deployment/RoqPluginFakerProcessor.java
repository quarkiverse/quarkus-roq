package io.quarkiverse.roq.plugin.faker.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontmatterTemplateUtils.getIncludeFilter;
import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontmatterTemplateUtils.normalizedLayout;
import static io.quarkiverse.roq.util.PathUtils.slugify;
import static io.quarkiverse.roq.util.PathUtils.toUnixPath;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.github.javafaker.Book;
import com.github.javafaker.Faker;

import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RoqPluginFakerProcessor {

    private static final Faker faker = new Faker();

    private static final List<String> TAGS = Arrays.asList(
            "Fiction", "Non-Fiction", "Science Fiction", "Fantasy", "Mystery",
            "Thriller", "Romance", "Historical", "Biography", "Autobiography",
            "Self-Help", "Philosophy", "Psychology", "Travel", "Adventure",
            "Young Adult", "Children's Books", "Classic Literature", "Poetry",
            "Drama", "Graphic Novels", "Comics", "Horror", "Crime",
            "Contemporary", "Literary Fiction", "Short Stories", "Memoir",
            "Spirituality", "Education");

    public record Document(String title,
            String description,
            String author,
            ZonedDateTime date,
            List<String> tags,
            String content) {

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass())
                return false;
            Document document = (Document) o;
            return Objects.equals(title, document.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(title);
        }
    }

    private static volatile FakerConfig FAKER_CONFIG;
    private static volatile Set<Document> FAKES;

    public static Document generateDocument(RoqSiteConfig siteConfig, FakerConfig fakerConfig) {
        final Book book = faker.book();
        String title = book.title() + " - " + book.genre() + " by " + book.author();
        String description = faker.lorem().sentence(10);
        String author = book.author();
        ZonedDateTime date = ZonedDateTime.ofInstant(faker.date().past(730, TimeUnit.DAYS).toInstant(),
                siteConfig.timeZoneOrDefault());
        final ArrayList<String> t = new ArrayList<>(TAGS);
        Collections.shuffle(t);
        List<String> tags = t.subList(0, faker.number().numberBetween(1, 5));
        String content = generateContent();
        return new Document(title, description, author, date, tags, content);
    }

    private static String generateContent() {
        int paragraphs = faker.number().numberBetween(2, 5);
        return IntStream.range(0, paragraphs).mapToObj(i -> faker.lorem().paragraph(faker.number().numberBetween(3, 6)))
                .collect(Collectors.joining("\n\n"));
    }

    public static Set<Document> generateDocuments(RoqSiteConfig siteConfig, FakerConfig fakerConfig, int count) {
        return IntStream.range(0, count).mapToObj(i -> generateDocument(siteConfig, fakerConfig)).collect(Collectors.toSet());
    }

    @BuildStep
    public void generateDocs(
            FakerConfig fakerConfig,
            RoqProjectBuildItem roqProject,
            RoqSiteConfig siteConfig,
            BuildProducer<RoqFrontMatterRawTemplateBuildItem> rawTemplatesProducer) {
        if (fakerConfig.count() == null || fakerConfig.count().isEmpty()) {
            return;
        }
        final Map<String, ConfiguredCollection> collections = siteConfig.collections().stream()
                .collect(Collectors.toMap(ConfiguredCollection::id, Function.identity()));
        for (Map.Entry<String, Integer> entry : fakerConfig.count().entrySet()) {
            final ConfiguredCollection collection = collections.get(entry.getKey());
            if (collection == null) {
                continue;
            }
            if (FAKES == null || !fakerConfig.equals(FAKER_CONFIG)) {
                FAKER_CONFIG = fakerConfig;
                FAKES = generateDocuments(siteConfig, fakerConfig, entry.getValue());
            }
            for (Document document : FAKES) {
                final String id = entry.getKey() + "/" + slugify(document.title(), false, false);
                final String path = id + ".md";
                final String layoutId = normalizedLayout(siteConfig.theme(),
                        null,
                        collection.layout());
                rawTemplatesProducer.produce(new RoqFrontMatterRawTemplateBuildItem(
                        TemplateSource.create(
                                id,
                                "markdown",
                                new SourceFile(
                                        toUnixPath(roqProject.project().roqDir().normalize().toAbsolutePath().toString()),
                                        path),
                                path,
                                id + ".html",
                                false,
                                true,
                                false,
                                false),
                        layoutId,
                        RoqFrontMatterRawTemplateBuildItem.TemplateType.DOCUMENT_PAGE,
                        new JsonObject()
                                .put("title", document.title())
                                .put("description", document.description())
                                .put("author", document.author())
                                .put("date", document.date().format(DateTimeFormatter.ofPattern(siteConfig.dateFormat())))
                                .put("tags", new JsonArray(document.tags())),
                        collection,
                        getIncludeFilter(layoutId).apply(document.content()),
                        document.content(),
                        List.of()));
            }
        }
    }

}
