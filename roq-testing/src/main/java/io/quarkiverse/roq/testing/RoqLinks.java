package io.quarkiverse.roq.testing;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.jboss.logging.Logger;

import io.mvnpm.raclette.checker.StaticSiteChecker;
import io.mvnpm.raclette.collector.Collector;
import io.mvnpm.raclette.collector.Input;
import io.mvnpm.raclette.types.Status;
import io.mvnpm.raclette.types.Uri;

/**
 * Static utility for link checking in {@link RoqAndRoll} tests.
 * <p>
 * Preconfigured automatically when {@code @RoqAndRoll} is used, the generated site
 * output directory is set after site generation, similar to how RestAssured gets its
 * port configured.
 *
 * <pre>
 * &#64;QuarkusTest
 * &#64;RoqAndRoll
 * public class MyTest {
 *     &#64;Test
 *     public void checkLinks() {
 *         RoqLinks.checkInternal();
 *     }
 * }
 * </pre>
 */
public final class RoqLinks {

    private static final Logger LOG = Logger.getLogger(RoqLinks.class);
    private static final String DEFAULT_EXCLUDES = ".*/q/dev-ui/.*";

    private static volatile Path outputDir;

    private RoqLinks() {
    }

    static void configure(Path outputDir) {
        RoqLinks.outputDir = outputDir;
    }

    static void reset() {
        RoqLinks.outputDir = null;
    }

    /**
     * Collect all links from the generated site directory.
     */
    public static Set<Uri> collect() {
        requireConfigured();
        long start = System.nanoTime();
        try (Collector collector = Collector.builder()
                .base(outputDir.toAbsolutePath().toUri().toString())
                .build()) {
            Set<Uri> links = collector.collectLinks(Set.of(new Input.FsPath(outputDir)));
            long ms = (System.nanoTime() - start) / 1_000_000;
            LOG.infof("Collected %d links in %dms", links.size(), ms);
            return links;
        }
    }

    /**
     * Check all links (internal and external) from the generated site.
     *
     * @return map of broken links to their status
     */
    public static Map<Uri, Status> checkAll() {
        return checkAll(UnaryOperator.identity());
    }

    /**
     * Check all links with custom StaticSiteChecker configuration.
     *
     * @param customizer function to customize the StaticSiteChecker builder
     * @return map of broken links to their status
     */
    public static Map<Uri, Status> checkAll(UnaryOperator<StaticSiteChecker.Builder> customizer) {
        requireConfigured();
        StaticSiteChecker.Builder builder = StaticSiteChecker.builder()
                .path(outputDir)
                .checkRemoteLinks(true)
                .excludes(DEFAULT_EXCLUDES);
        builder = customizer.apply(builder);
        long start = System.nanoTime();
        try (StaticSiteChecker checker = builder.build()) {
            Map<Uri, Status> broken = checker.check();
            long ms = (System.nanoTime() - start) / 1_000_000;
            logBrokenLinks("all", broken, ms);
            return broken;
        }
    }

    /**
     * Check all internal links directly against the generated site files on disk.
     * Uses Raclette's StaticSiteChecker with index.html fallback and localhost
     * URL rewriting.
     *
     * @return map of broken links to their status
     */
    public static Map<Uri, Status> checkInternal() {
        requireConfigured();
        long start = System.nanoTime();
        try (StaticSiteChecker checker = StaticSiteChecker.builder()
                .path(outputDir)
                .excludes(DEFAULT_EXCLUDES)
                .build()) {
            Map<Uri, Status> broken = checker.check();
            long ms = (System.nanoTime() - start) / 1_000_000;
            logBrokenLinks("internal", broken, ms);
            return broken;
        }
    }

    private static void logBrokenLinks(String scope, Map<Uri, Status> broken, long ms) {
        if (broken.isEmpty()) {
            LOG.infof("Checked %s links in %dms, no broken links", scope, ms);
        } else {
            LOG.warnf("Checked %s links in %dms, found %d broken link(s):", scope, ms, broken.size());
            broken.forEach((uri, status) -> LOG.warnf("  %s -> %s", uri, status));
        }
    }

    /**
     * @return the generated site output directory
     */
    public static Path outputDir() {
        requireConfigured();
        return outputDir;
    }

    private static void requireConfigured() {
        if (outputDir == null) {
            throw new IllegalStateException(
                    "RoqLinks is not configured. Make sure your test class is annotated with @RoqAndRoll.");
        }
    }
}
