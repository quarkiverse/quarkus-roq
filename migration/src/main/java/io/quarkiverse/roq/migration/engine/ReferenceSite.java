package io.quarkiverse.roq.migration.engine;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.quarkiverse.roq.migration.SiteFiles;

/**
 * The site as it was before the migration, usually the live site, which {@link SiteMigrator#validate} compares the
 * built site with.
 * <p>
 * Nothing is crawled: the built site says which pages there are, and the reference site is only asked for those.
 */
public final class ReferenceSite {

    /**
     * How many pages are requested at once, to keep the load on the reference site low.
     */
    static final int REQUESTS_AT_ONCE = 4;

    private final URI url;
    private final Path builtSite;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * @param builtSite the site the {@link SiteBuilder} generated, e.g. {@code target/roq/} in the converted site
     */
    ReferenceSite(URI url, Path builtSite) {
        this.url = url;
        this.builtSite = builtSite;
    }

    public URI url() {
        return url;
    }

    /**
     * @return the paths of the built pages, e.g. {@code /} and {@code /posts/hello/}; not the 404 page
     */
    public List<String> builtPages() throws IOException {
        List<String> pages = new ArrayList<>();
        for (Path page : SiteFiles.files(builtSite, p -> p.toString().endsWith(".html"))) {
            String path = "/" + SiteFiles.relativePath(builtSite, page);
            if (!path.equals("/404.html")) {
                pages.add(path.endsWith("/index.html") ? path.substring(0, path.length() - "index.html".length()) : path);
            }
        }
        return pages.stream().sorted().toList();
    }

    /**
     * @return the built pages that the reference site does not serve, with the status it answered, e.g.
     *         {@code /posts/hello/ (404)}
     */
    public List<String> missingPages() throws IOException, InterruptedException {
        List<String> pages = builtPages();
        ExecutorService requests = Executors.newFixedThreadPool(REQUESTS_AT_ONCE);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (String page : pages) {
                statuses.add(requests.submit(() -> status(page)));
            }
            List<String> missing = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) {
                int status = statuses.get(i).get();
                if (status < 200 || status >= 300) {
                    missing.add(pages.get(i) + " (" + status + ")");
                }
            }
            return missing;
        } catch (ExecutionException e) {
            throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
        } finally {
            requests.shutdownNow();
        }
    }

    /**
     * Checks that the reference site answers, so that a site that cannot be reached is not reported page by page.
     */
    void checkReachable() throws IOException, InterruptedException {
        status("/");
    }

    private int status(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(resolve(path)).timeout(Duration.ofSeconds(20)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private URI resolve(String path) {
        String base = url.toString();
        return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path);
    }
}
