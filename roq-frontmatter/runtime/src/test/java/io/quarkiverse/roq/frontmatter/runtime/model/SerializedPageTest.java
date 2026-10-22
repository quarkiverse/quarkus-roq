package io.quarkiverse.roq.frontmatter.runtime.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class SerializedPageTest {

    private static final RootUrl ROOT = new RootUrl("https://example.com", "/blog");

    private static PageSource source(String id, PageFiles files) {
        return new PageSource(
                TemplateSource.create(id, "html", new SourceFile("/site", "content/" + id),
                        id, "roq-gen/" + id, false, true, false, false),
                false, "2024-01-01T00:00:00Z", files, false);
    }

    private static Page serializeAndRestore(SerializedPage serialized) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(serialized);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            return ((SerializedPage) ois.readObject()).toPage();
        }
    }

    @Test
    void normalPageRoundTrip() throws Exception {
        JsonObject data = new JsonObject().put("title", "Hello").put("tags", new JsonArray().add("java"));
        SerializedPage serialized = SerializedPage.ofNormal(
                new RoqUrl(ROOT, "about"), source("about.html", PageFiles.empty()), data, null);

        Page page = serializeAndRestore(serialized);

        assertInstanceOf(NormalPage.class, page);
        NormalPage normal = (NormalPage) page;
        assertEquals("about.html", normal.id());
        assertEquals("/blog/about", normal.url().path());
        assertEquals("Hello", normal.data().getString("title"));
        assertNull(normal.paginator());
    }

    @Test
    void normalPageWithPaginatorRoundTrip() throws Exception {
        Paginator paginator = new Paginator("posts", 10, 5, 2, 1,
                new RoqUrl(ROOT, ""), null, null, 2, new RoqUrl(ROOT, "page/2"),
                List.of(new RoqUrl(ROOT, ""), new RoqUrl(ROOT, "page/2")));
        SerializedPage serialized = SerializedPage.ofNormal(
                new RoqUrl(ROOT, ""), source("index.html", PageFiles.empty()),
                new JsonObject().put("title", "Index"), paginator);

        NormalPage normal = (NormalPage) serializeAndRestore(serialized);
        assertNotNull(normal.paginator());
        assertEquals("posts", normal.paginator().collection());
        assertEquals(10, normal.paginator().collectionSize());
        assertEquals(2, normal.paginator().total());
    }

    @Test
    void documentPageRoundTrip() throws Exception {
        JsonObject data = new JsonObject()
                .put("title", "My Post")
                .put("nested", new JsonObject().put("key", "value"));
        PageFiles files = new PageFiles(List.of("image.png", "doc.pdf"), true);
        SerializedPage serialized = SerializedPage.ofDocument(
                "posts", new RoqUrl(ROOT, "posts/my-post"),
                source("posts/my-post.md", files), data, false);

        Page page = serializeAndRestore(serialized);

        assertInstanceOf(DocumentPage.class, page);
        DocumentPage doc = (DocumentPage) page;
        assertEquals("posts", doc.collectionId());
        assertEquals("My Post", doc.data().getString("title"));
        assertEquals("value", doc.data().getJsonObject("nested").getString("key"));
        assertFalse(doc.hidden());
        assertTrue(doc.source().files().contains("image.png"));
    }

    @Test
    void documentPageHiddenRoundTrip() throws Exception {
        SerializedPage serialized = SerializedPage.ofDocument(
                "drafts", new RoqUrl(ROOT, "drafts/secret"),
                source("drafts/secret.md", null), new JsonObject(), true);

        DocumentPage doc = (DocumentPage) serializeAndRestore(serialized);
        assertTrue(doc.hidden());
    }

    @Test
    void externalUrlRoundTrip() throws Exception {
        RoqUrl externalUrl = new RoqUrl(null, "https://external.com/page");
        SerializedPage serialized = SerializedPage.ofNormal(
                externalUrl, source("ext.html", PageFiles.empty()), new JsonObject(), null);

        Page page = serializeAndRestore(serialized);
        assertTrue(page.url().isExternal());
        assertEquals("https://external.com/page", page.url().path());
    }
}
