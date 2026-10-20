package io.quarkiverse.roq.frontmatter.runtime.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.EncodedJson;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.Paginator;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RecordedPagesTest {

    private static final RootUrl ROOT = new RootUrl("https://example.com", "/blog");

    private static PageSource source(String id, PageFiles files, SourceFile file) {
        return new PageSource(
                TemplateSource.create(id, "html", file, id, "roq-gen/" + id, false, true, false, false),
                false, "2024-01-01T00:00:00Z", files, false);
    }

    /**
     * Simulates the bytecode recorder: the payload is recorded as chunks and rebuilt with the recordable constructor.
     */
    private static <T> T record(Object json) {
        final EncodedJson encoded = EncodedJson.of(json);
        return new EncodedJson(encoded.isArray(), encoded.getChunks()).get();
    }

    @Test
    void documentsRoundTrip() {
        final DocumentPage doc = new DocumentPage("posts", new RoqUrl(ROOT, "posts/hello"),
                source("posts/hello.md", new PageFiles(List.of("a.png", "b.png"), true),
                        new SourceFile("/site", "content/posts/hello.md")),
                new JsonObject().put("title", "Hello").put("tags", new JsonArray().add("a")), true);
        final DocumentPage noFiles = new DocumentPage("posts", new RoqUrl(ROOT, "posts/other"),
                source("posts/other.md", null, null), new JsonObject(), true);

        final JsonArray encoded = new JsonArray()
                .add(RecordedPages.encodeDocument(ROOT, doc.url(), doc.source(), doc.data()))
                .add(RecordedPages.encodeDocument(ROOT, noFiles.url(), noFiles.source(), noFiles.data()));
        final List<DocumentPage> decoded = RecordedPages.decodeDocuments(ROOT, "posts", true, record(encoded));

        assertEquals(List.of(doc, noFiles), decoded);
        assertEquals(doc.source(), decoded.get(0).source());
        assertNull(decoded.get(1).source().files());
        assertEquals("/blog/posts/hello", decoded.get(0).url().path());
    }

    @Test
    void normalPagesRoundTrip() {
        final Paginator paginator = new Paginator("posts", 5, 2, 3, 2,
                new RoqUrl(ROOT, ""), 1, new RoqUrl(ROOT, ""), 3, new RoqUrl(ROOT, "page3"),
                List.of(new RoqUrl(ROOT, ""), new RoqUrl(ROOT, "page2"), new RoqUrl(ROOT, "page3")));
        final NormalPage paginated = new NormalPage(new RoqUrl(ROOT, "page2"),
                source("index.html", PageFiles.empty(), new SourceFile("/site", "content/index.html")),
                new JsonObject().put("title", "Index"), paginator);
        final Paginator lastPage = new Paginator("posts", 5, 2, 3, 3, new RoqUrl(ROOT, ""), 2,
                new RoqUrl(ROOT, "page2"), null, null, List.of());
        final NormalPage external = new NormalPage(new RoqUrl(null, "https://external.com/page"),
                source("external.html", PageFiles.empty(), null), new JsonObject(), lastPage);
        final NormalPage simple = new NormalPage(new RoqUrl(ROOT, "about"),
                source("about.html", PageFiles.empty(), null), new JsonObject(), null);

        final JsonArray encoded = new JsonArray();
        for (NormalPage p : List.of(paginated, external, simple)) {
            encoded.add(RecordedPages.encodeNormalPage(ROOT, p.url(), p.source(), p.data(), p.paginator()));
        }
        final List<NormalPage> decoded = RecordedPages.decodeNormalPages(ROOT, record(encoded));

        assertEquals(List.of(paginated, external, simple), decoded);
        assertEquals(paginator, decoded.get(0).paginator());
        assertEquals(lastPage, decoded.get(1).paginator());
        assertTrue(decoded.get(1).url().isExternal());
        assertNull(decoded.get(2).paginator());
    }

    @Test
    void templateSourcesRoundTrip() {
        final List<TemplateSource> sources = List.of(
                TemplateSource.create("layouts/default.html", "html", new SourceFile("/site", "templates/layouts/default.html"),
                        "layouts/default.html", "layouts/default.html", true, true, false, false),
                TemplateSource.create("feed.xml", null, null, "feed.xml", "roq-gen/feed.xml", false, false, false, false));

        assertEquals(sources, RecordedPages.decodeTemplateSources(record(RecordedPages.encodeTemplateSources(sources))));
    }

    @Test
    void pageIdsByPathRoundTrip() {
        final Map<String, String> pageIdsByPath = Map.of("posts/hello/", "posts/hello.md", "feed.xml", "feed.xml");
        final EncodedJson encoded = EncodedJson.of(RecordedPages.encodePageIdsByPath(pageIdsByPath));

        assertEquals(pageIdsByPath,
                RecordedPages.decodePageIdsByPath(new EncodedJson(encoded.isArray(), encoded.getChunks())));
    }

    @Test
    void largePayloadIsChunked() {
        final JsonArray encoded = new JsonArray();
        for (int i = 0; i < 2_000; i++) {
            encoded.add(RecordedPages.encodeDocument(ROOT, new RoqUrl(ROOT, "posts/post-" + i),
                    source("posts/post-" + i + ".md", PageFiles.empty(), null), new JsonObject().put("i", i)));
        }
        assertTrue(EncodedJson.of(encoded).getChunks().size() > 1);
        final List<DocumentPage> decoded = RecordedPages.decodeDocuments(ROOT, "posts", false, record(encoded));
        assertEquals(2_000, decoded.size());
        assertEquals("posts/post-1999.md", decoded.get(1_999).id());
    }

    @Test
    void urlWithForeignRootIsRejected() {
        final RoqUrl url = new RoqUrl(new RootUrl("https://other.com", "/"), "posts/hello");
        assertThrows(IllegalStateException.class,
                () -> RecordedPages.encodeDocument(ROOT, url, source("posts/hello.md", null, null), new JsonObject()));
    }
}
