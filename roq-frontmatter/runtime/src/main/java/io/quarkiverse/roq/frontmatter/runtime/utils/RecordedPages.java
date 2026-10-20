package io.quarkiverse.roq.frontmatter.runtime.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

/**
 * JSON codec for the page model, used to record pages in bulk.
 * <p>
 * Recording each page as its own object adds a handful of constants per page to a single generated
 * recorder class, overflowing the JVM constant pool limit (65,535 entries) on large sites.
 * Pages are instead encoded into one JSON array, recorded as a few chunked strings
 * (see {@link io.quarkiverse.roq.EncodedJson}), and decoded at runtime.
 * <p>
 * All urls of a site share the same {@link RootUrl}, it is recorded once and only the resource path is encoded.
 */
public final class RecordedPages {

    private RecordedPages() {
    }

    public static JsonObject encodeDocument(RootUrl root, RoqUrl url, PageSource source, JsonObject data) {
        return new JsonObject()
                .put("url", encodeUrl(root, url))
                .put("source", encodePageSource(source))
                .put("data", data);
    }

    public static List<DocumentPage> decodeDocuments(RootUrl root, String collectionId, boolean hidden,
            JsonArray documents) {
        final List<DocumentPage> result = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            final JsonObject d = documents.getJsonObject(i);
            result.add(new DocumentPage(collectionId, decodeUrl(root, d.getValue("url")),
                    decodePageSource(d.getJsonObject("source")), d.getJsonObject("data"), hidden));
        }
        return result;
    }

    public static JsonObject encodeNormalPage(RootUrl root, RoqUrl url, PageSource source, JsonObject data,
            Paginator paginator) {
        return new JsonObject()
                .put("url", encodeUrl(root, url))
                .put("source", encodePageSource(source))
                .put("data", data)
                .put("paginator", encodePaginator(root, paginator));
    }

    public static List<NormalPage> decodeNormalPages(RootUrl root, JsonArray pages) {
        final List<NormalPage> result = new ArrayList<>(pages.size());
        for (int i = 0; i < pages.size(); i++) {
            final JsonObject p = pages.getJsonObject(i);
            result.add(new NormalPage(decodeUrl(root, p.getValue("url")), decodePageSource(p.getJsonObject("source")),
                    p.getJsonObject("data"), decodePaginator(root, p.getJsonObject("paginator"))));
        }
        return result;
    }

    public static JsonArray encodeTemplateSources(List<TemplateSource> sources) {
        final JsonArray result = new JsonArray();
        for (TemplateSource source : sources) {
            result.add(encodeTemplateSource(source));
        }
        return result;
    }

    public static List<TemplateSource> decodeTemplateSources(JsonArray sources) {
        final List<TemplateSource> result = new ArrayList<>(sources.size());
        for (int i = 0; i < sources.size(); i++) {
            result.add(decodeTemplateSource(sources.getJsonObject(i)));
        }
        return result;
    }

    public static JsonObject encodePageIdsByPath(Map<String, String> pageIdsByPath) {
        final JsonObject result = new JsonObject();
        pageIdsByPath.forEach(result::put);
        return result;
    }

    public static Map<String, String> decodePageIdsByPath(EncodedJson pageIdsByPath) {
        final JsonObject json = pageIdsByPath.get();
        final Map<String, String> result = new HashMap<>(json.size());
        for (Map.Entry<String, Object> e : json) {
            result.put(e.getKey(), (String) e.getValue());
        }
        return result;
    }

    static JsonObject encodePageSource(PageSource source) {
        return new JsonObject()
                .put("template", encodeTemplateSource(source.template()))
                .put("draft", source.draft())
                .put("dateString", source.dateString())
                .put("files", source.files() == null ? null
                        : new JsonObject()
                                .put("names", new JsonArray(source.files().names()))
                                .put("slugified", source.files().slugified()))
                .put("generated", source.generated());
    }

    static PageSource decodePageSource(JsonObject json) {
        final JsonObject files = json.getJsonObject("files");
        return new PageSource(
                decodeTemplateSource(json.getJsonObject("template")),
                json.getBoolean("draft"),
                json.getString("dateString"),
                files == null ? null : new PageFiles(decodeStrings(files.getJsonArray("names")), files.getBoolean("slugified")),
                json.getBoolean("generated"));
    }

    static JsonObject encodeTemplateSource(TemplateSource source) {
        if (source == null) {
            return null;
        }
        return new JsonObject()
                .put("id", source.id())
                .put("markup", source.markup())
                .put("file", source.file() == null ? null
                        : new JsonObject()
                                .put("siteDirPath", source.file().siteDirPath())
                                .put("relativePath", source.file().relativePath()))
                .put("path", source.path())
                .put("generatedQuteId", source.generatedQuteId())
                .put("isLayout", source.isLayout())
                .put("isTargetHtml", source.isTargetHtml())
                .put("isIndex", source.isIndex())
                .put("isSiteIndex", source.isSiteIndex())
                .put("targetExtension", source.targetExtension());
    }

    static TemplateSource decodeTemplateSource(JsonObject json) {
        if (json == null) {
            return null;
        }
        final JsonObject file = json.getJsonObject("file");
        return new TemplateSource(
                json.getString("id"),
                json.getString("markup"),
                file == null ? null : new SourceFile(file.getString("siteDirPath"), file.getString("relativePath")),
                json.getString("path"),
                json.getString("generatedQuteId"),
                json.getBoolean("isLayout"),
                json.getBoolean("isTargetHtml"),
                json.getBoolean("isIndex"),
                json.getBoolean("isSiteIndex"),
                json.getString("targetExtension"));
    }

    static JsonObject encodePaginator(RootUrl root, Paginator paginator) {
        if (paginator == null) {
            return null;
        }
        final JsonArray pagesUrl = new JsonArray();
        for (RoqUrl url : paginator.pagesUrl()) {
            pagesUrl.add(encodeUrl(root, url));
        }
        return new JsonObject()
                .put("collection", paginator.collection())
                .put("collectionSize", paginator.collectionSize())
                .put("limit", paginator.limit())
                .put("total", paginator.total())
                .put("currentIndex", paginator.currentIndex())
                .put("firstUrl", encodeUrl(root, paginator.firstUrl()))
                .put("previousIndex", paginator.previousIndex())
                .put("previous", encodeUrl(root, paginator.previous()))
                .put("nextIndex", paginator.nextIndex())
                .put("next", encodeUrl(root, paginator.next()))
                .put("pagesUrl", pagesUrl);
    }

    static Paginator decodePaginator(RootUrl root, JsonObject json) {
        if (json == null) {
            return null;
        }
        final JsonArray encodedPagesUrl = json.getJsonArray("pagesUrl");
        final List<RoqUrl> pagesUrl = new ArrayList<>(encodedPagesUrl.size());
        for (int i = 0; i < encodedPagesUrl.size(); i++) {
            pagesUrl.add(decodeUrl(root, encodedPagesUrl.getValue(i)));
        }
        return new Paginator(
                json.getString("collection"),
                json.getInteger("collectionSize"),
                json.getInteger("limit"),
                json.getInteger("total"),
                json.getInteger("currentIndex"),
                decodeUrl(root, json.getValue("firstUrl")),
                json.getInteger("previousIndex"),
                decodeUrl(root, json.getValue("previous")),
                json.getInteger("nextIndex"),
                decodeUrl(root, json.getValue("next")),
                pagesUrl);
    }

    /**
     * Urls relative to the site root are encoded as their resource path, external urls (without root)
     * as {@code {"external": "..."}}.
     */
    static Object encodeUrl(RootUrl root, RoqUrl url) {
        if (url == null) {
            return null;
        }
        if (url.root() == null) {
            return new JsonObject().put("external", url.resourcePath());
        }
        if (!url.root().equals(root)) {
            throw new IllegalStateException(
                    "Url '%s' has a root (%s) different from the site root (%s)".formatted(url, url.root(), root));
        }
        return url.resourcePath();
    }

    static RoqUrl decodeUrl(RootUrl root, Object json) {
        if (json == null) {
            return null;
        }
        if (json instanceof JsonObject external) {
            return new RoqUrl(null, external.getString("external"));
        }
        return new RoqUrl(root, (String) json);
    }

    private static List<String> decodeStrings(JsonArray array) {
        final List<String> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            result.add(array.getString(i));
        }
        return result;
    }
}
