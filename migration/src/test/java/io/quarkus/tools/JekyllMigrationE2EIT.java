package io.quarkus.tools;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import io.quarkiverse.roq.testing.RoqAndRoll;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Playwright e2e tests for a Jekyll site. These tests verify rendered page content
 * and are designed to pass against both:
 * <ul>
 * <li>the original Jekyll site (served by {@code jekyll serve})</li>
 * <li>the migrated Roq site (served by {@code @RoqAndRoll} or {@code roq dev})</li>
 * </ul>
 *
 * The tests use a static fixture ({@code src/test/resources/jekyll-site/}) rather
 * than scaffolding with {@code jekyll new} at test time. This avoids a Ruby/gem
 * dependency (slow install, fragile in CI) and gives us control over the fixture
 * content. {@code jekyll new} wouldn't help anyway — it doesn't include layout
 * templates (those live in the minima theme gem), so we'd still need hand-written
 * Liquid templates to exercise the converter.
 *
 * <h2>Default mode (RoqAndRoll)</h2>
 * <p>
 * Without any extra flags, the test boots Quarkus and serves the pre-converted
 * Roq fixture via {@code @RoqAndRoll}:
 * </p>
 *
 * <pre>{@code
 *   mvn -f migration verify
 * }</pre>
 *
 * <h2>External URL mode (Jekyll or manual Roq)</h2>
 * <p>
 * Set {@code test.base.url} to run the same assertions against any server.
 * Quarkus still boots (ignored), but the tests hit the external URL.
 * </p>
 *
 * <pre>{@code
 *   # Against Jekyll:
 *   cd migration/src/test/resources/jekyll-site && jekyll serve
 *   mvn -f migration verify -Dtest.base.url=http://localhost:4000
 *
 *   # Against a manually started Roq site:
 *   cp -r migration/src/test/resources/jekyll-site /tmp/my-roq-site
 *   ./migration/roq-it-jekyll /tmp/my-roq-site
 *   mvn -f migration verify -Dtest.base.url=http://localhost:8080
 * }</pre>
 */
@QuarkusTest
@RoqAndRoll
class JekyllMigrationE2EIT {

    static Playwright playwright;
    static Browser browser;

    @BeforeAll
    static void setUp() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void tearDown() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    private String baseUrl() {
        String external = System.getProperty("test.base.url");
        return external != null ? external : "http://localhost:8082";
    }

    @Test
    void homePageShowsSiteTitleAndPostLink() {
        try (Page page = browser.newPage()) {
            page.navigate(baseUrl() + "/");

            assertThat(page).hasTitle(Pattern.compile(".*Home.*"));
            assertThat(page.locator(".post-list")).isVisible();
            assertThat(page.locator("a.post-link")).containsText("Hello World");
        }
    }

    @Test
    void blogPostShowsTitleAndContent() {
        try (Page page = browser.newPage()) {
            // Navigate from home to avoid hardcoding post URLs, which differ
            // between Jekyll (/:categories/:year/:month/:day/:title.html)
            // and Roq (/posts/:title).
            page.navigate(baseUrl() + "/");
            page.locator("a.post-link", new Page.LocatorOptions().setHasText("Hello World")).click();

            assertThat(page.locator("h1")).containsText("Hello World");
            assertThat(page.locator("article")).containsText("Welcome to the blog");
            assertThat(page.locator("article")).containsText("Test Author");
        }
    }

    @Test
    void aboutPageShowsContent() {
        try (Page page = browser.newPage()) {
            // The about page permalink (/company/about/) becomes an alias in Roq,
            // but both servers should serve content at this path (Roq via redirect).
            page.navigate(baseUrl() + "/company/about/");

            assertThat(page.locator("body")).containsText("This is the about page");
        }
    }
}
