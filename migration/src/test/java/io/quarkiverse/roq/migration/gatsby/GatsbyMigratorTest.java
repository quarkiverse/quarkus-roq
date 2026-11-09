package io.quarkiverse.roq.migration.gatsby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.roq.migration.engine.MigrationException;
import io.quarkiverse.roq.migration.engine.MigrationOptions;
import io.quarkiverse.roq.migration.engine.MigrationOutput;
import io.quarkiverse.roq.migration.engine.MigrationReport;
import io.quarkiverse.roq.migration.engine.MigrationRunner;
import io.quarkiverse.roq.migration.engine.MigrationRunner.MigrationResult;

class GatsbyMigratorTest {

    @TempDir
    Path tmp;

    Path repo;
    Path out;

    /**
     * What the migration said, apart from warnings.
     */
    static final List<String> INFO = new java.util.ArrayList<>();

    static final MigrationOutput QUIET = new MigrationOutput() {
        @Override
        public void info(String message) {
            INFO.add(message.strip());
        }

        @Override
        public void warn(String message) {
        }
    };

    /**
     * A small repository shaped like the Quarkiverse Hub: a Gatsby project in site/ rendering the wiki-style
     * Markdown pages in docs/.
     */
    @BeforeEach
    void createRepository() throws IOException {
        repo = tmp.resolve("hub");
        out = tmp.resolve("hub-roq");
        write(Map.ofEntries(
                Map.entry("README.md", "# The hub\n"),
                Map.entry("site/package.json", """
                        {
                          "dependencies": {"gatsby": "^5.14.0"},
                          "scripts": {
                            "serve": "gatsby serve",
                            "test": "jest --testPathIgnorePatterns test-integration/",
                            "test:int": "jest --config=jest.integration.config.js test-integration/"
                          }
                        }
                        """),
                Map.entry("site/package-lock.json", "{}"),
                Map.entry("site/babel.config.js", "module.exports = { presets: [\"@babel/preset-env\"] }\n"),
                Map.entry("site/jest.config.js", "module.exports = { setupFiles: [\"<rootDir>/loadershim.js\"] }\n"),
                Map.entry("site/loadershim.js", "global.___loader = {}\n"),
                Map.entry("site/jest.integration.config.js", """
                        module.exports = {
                            preset: "jest-puppeteer",
                            testEnvironment: "./test-integration/debugenv.js",
                        }
                        """),
                Map.entry("site/jest-puppeteer.config.js", """
                        module.exports = {
                          server: {
                            command: "./node_modules/.bin/gatsby serve",
                            port: 9000,
                          },
                        }
                        """),
                Map.entry("site/test-integration/debugenv.js", "module.exports = class DebugEnv {}\n"),
                Map.entry("site/test-integration/frontpage.test.js", """
                        const config = require("../gatsby-config.js")
                        const {port} = require("../jest-puppeteer.config").server
                        it("has a front page", async () => {
                            await page.goto(`http://localhost:${port}`)
                        })
                        """),
                Map.entry("site/gatsby-config.js", GatsbyConfigTest.CONFIG),
                Map.entry("site/gatsby-browser.js", """
                        import "./src/normalize.css"
                        import "./src/style.css"
                        import "prismjs/themes/prism.css"
                        import "@fontsource/open-sans"
                        import "@fontsource/open-sans/300.css"
                        """),
                Map.entry("site/gatsby-node.js", "exports.createPages = async ({ actions }) => {\n}\n"),
                Map.entry("site/src/normalize.css", "html { margin: 0; }\n"),
                Map.entry("site/src/style.css", "body { color: #333; }\n"),
                Map.entry("site/src/components/layout.css", "main { padding: 1em; }\n"),
                Map.entry("site/src/components/index.module.css", ".hero { color: red; }\n"),
                Map.entry("site/src/components/header.js", "import styled from \"styled-components\";\n"),
                Map.entry("site/src/components/header.test.js", "it('renders', () => {})\n"),
                Map.entry("site/src/images/logo.svg", "<svg/>"),
                Map.entry("site/src/pages/index.jsx", "export default HomePage\n"),
                Map.entry("site/src/pages/{markdownRemark.fields__slug}.jsx", """
                        import * as React from "react"
                        import Layout from "../components/layout"
                        import Page from "../components/page"
                        const DocsPage = ({data}) => <Layout><Page markdownRemark={data.markdownRemark}/></Layout>
                        export default DocsPage
                        """),
                // Only the Markdown route uses it, and what it uses
                Map.entry("site/src/components/page.js", """
                        import * as React from "react"
                        import Title from "./page-title"
                        const Page = ({markdownRemark}) => {
                            const {fields, html} = markdownRemark
                            return <><Title title={fields.title}/><div dangerouslySetInnerHTML={{__html: html}}/></>
                        }
                        export default Page
                        """),
                Map.entry("site/src/components/page-title.js", """
                        import * as React from "react"
                        const Title = ({title}) => <h1>{title}</h1>
                        export default Title
                        """),
                Map.entry("site/src/pages/404.js", "export default NotFoundPage\n"),
                Map.entry("site/plugins/gatsby-frontmatter-defaults/gatsby-node.js", "exports.onCreateNode = () => {}\n"),
                Map.entry("site/public/index.html", "<html>Gatsby build output</html>"),
                Map.entry("site/static/CNAME", "hub.example.org\n"),
                Map.entry("docs/Home.md", "# Welcome to the hub!\n\nSee [the checklist](checklist).\n"),
                // No ":" in file names, which Windows does not allow; the Hub has one, and Roq slugifies it away
                Map.entry("docs/Infrastructure-for-Quarkus-3.x-Manual-upgrade.md", "Upgrade by hand.\n"),
                Map.entry("docs/With-Slug.md", "---\ntitle: Has a slug\nslug: /custom/place\n---\nBody\n"),
                Map.entry("docs/_Footer.md", "Footer\n"),
                Map.entry(".github/workflows/wikisync.yml", "on:\n  push:\n    paths:\n      - \"docs/**\"\n"),
                Map.entry(".github/workflows/build.yml",
                        "steps:\n  - run: npm run build\n    working-directory: site/\n  - run: npm run test:int\n")));
    }

    MigrationResult migrate() throws MigrationException {
        INFO.clear();
        return new MigrationRunner(new GatsbyMigrator(), repo, site -> {
        }, site -> {
        }, new MigrationOptions(out, false, QUIET)).run();
    }

    List<String> actionItems(MigrationResult result) {
        return result.actionItems().stream().map(MigrationReport.ActionItem::message).toList();
    }

    @Test
    void commitsPublicAndIgnoresTarget() throws Exception {
        Files.writeString(repo.resolve(".gitignore"), "# gatsby files\n.cache/\npublic\n\n.DS_Store\n");

        migrate();

        // public/ has the images and static files now
        assertThat(out.resolve(".gitignore")).hasContent("# gatsby files\n.cache/\n\n.DS_Store\n\n"
                + "# Roq builds the site in target/\ntarget/");
        assertThat(GatsbyContent.BUILD_OUTPUT.matcher("/public/").matches()).isTrue();
        assertThat(GatsbyContent.BUILD_OUTPUT.matcher("site/public").matches()).isFalse();
    }

    @Test
    void namesTheProjectAfterTheSiteTitle() throws Exception {
        assertThat(migrate().analysis().projectName()).isEqualTo("quarkiverse-hub-website");
    }

    @Test
    void movesTheMarkdownSourceIntoContent() throws Exception {
        migrate();

        assertThat(out.resolve("content/Home.md")).exists();
        assertThat(out.resolve("content/Infrastructure-for-Quarkus-3.x-Manual-upgrade.md")).exists();
        assertThat(out.resolve("content/_Footer.md")).hasContent("Footer");
        assertThat(out.resolve("docs")).doesNotExist();
        assertThat(out.resolve("README.md")).exists();
    }

    /**
     * A 64x64 PNG.
     */
    static byte[] png() {
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                    "png", png);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return png.toByteArray();
    }

    @Test
    void makesTheFaviconAndManifestFromTheManifestPluginsIcon() throws Exception {
        Files.write(repo.resolve("site/src/images/logo.png"), png());
        List<String> items = actionItems(migrate());

        // Under the names {#favicon} discovers, at the size the icon is
        assertThat(out.resolve("public/images/favicon.png")).hasBinaryContent(png());
        assertThat(out.resolve("public/images/apple-touch-icon.png")).hasBinaryContent(png());
        assertThat(out.resolve("public/manifest.webmanifest")).hasContent("""
                {
                  "name": "Quarkiverse Hub",
                  "icons": [
                    { "src": "images/favicon.png", "sizes": "64x64", "type": "image/png" }
                  ]
                }
                """);
        assertThat(out.resolve("templates/layouts/main.html")).content().contains("""
                  {#favicon site /}
                  <link rel="manifest" href="{=site.url('manifest.webmanifest')}">
                  {#bundle /}
                """);
        // Named after the site rather than the Gatsby starter its options named
        assertThat(INFO).contains("Named the web app manifest after the site (Quarkiverse Hub), rather than the Gatsby "
                + "starter (gatsby-starter-default) that the site's gatsby-plugin-manifest options still named");
        assertThat(items).noneMatch(item -> item.contains("manifest.webmanifest"));
        // Without a site title, there is nothing better to name it after
        assertThat(GatsbyIcons.siteNamed(Map.of("name", "starter"), null)).containsEntry("name", "starter");
        assertThat(items).noneMatch(item -> item.startsWith("Add a web app manifest"));
    }

    @Test
    void givesTheManifestTheIconsSizeWhenItCanBeRead() throws Exception {
        Path icons = Files.createDirectories(tmp.resolve("icons"));
        Files.write(icons.resolve("icon.png"), png());
        Files.writeString(icons.resolve("icon.svg"), "<svg viewBox=\"0 0 10 10\"/>");
        Files.write(icons.resolve("icon.ico"), new byte[] { 0, 0, 1, 0 });

        assertThat(GatsbyIcons.sizes(icons.resolve("icon.png"))).isEqualTo("64x64");
        // An SVG scales; the JDK cannot read an ICO
        assertThat(GatsbyIcons.sizes(icons.resolve("icon.svg"))).isEqualTo("any");
        assertThat(GatsbyIcons.sizes(icons.resolve("icon.ico"))).isEqualTo("any");
    }

    @Test
    void saysWhenTheManifestPluginsIconIsMissing() throws Exception {
        List<String> items = actionItems(migrate());

        assertThat(items).contains("Add a favicon to public/images/: the icon src/images/logo.png that "
                + "gatsby-plugin-manifest made the favicon from was not found");
        assertThat(out.resolve("public/manifest.webmanifest")).doesNotExist();
        // Without a blank line where the favicon would be
        assertThat(out.resolve("templates/layouts/main.html")).content().contains("  {/if}\n  {#bundle /}\n</head>");
    }

    @Test
    void showsTheHomePageAtTheRootWithTheSiteMetadata() throws Exception {
        migrate();

        assertThat(out.resolve("content/index.html")).hasContent("""
                ---
                title: "Quarkiverse Hub"
                description: "The Quarkiverse Hub provides support for Quarkus extension projects."
                layout: main
                link: /
                ---
                {=site.pageContent(site.page('Home.md'))}
                """);
    }

    @Test
    void leavesPagesWithoutFrontMatterAsTheyAre() throws Exception {
        migrate();

        // So that whatever else uses them, such as a GitHub wiki sync, sees the same files
        assertThat(out.resolve("content/Home.md")).hasContent("# Welcome to the hub!\n\nSee [the checklist](checklist).");
        assertThat(out.resolve("content/Infrastructure-for-Quarkus-3.x-Manual-upgrade.md")).hasContent("Upgrade by hand.");
    }

    @Test
    void turnsASlugIntoALink() throws Exception {
        migrate();

        assertThat(out.resolve("content/With-Slug.md")).hasContent("""
                ---
                title: Has a slug
                link: /custom/place/
                ---
                Body
                """);
    }

    @Test
    void configuresGatsbyStyleUrls() throws Exception {
        migrate();

        assertThat(out.resolve("config/application.properties")).hasContent("""
                site.url=https://hub.quarkiverse.io
                site.page-link=/:path/
                quarkus.qute.alt-expr-syntax=true
                """);
    }

    @Test
    void copiesImagesAndStaticFilesToPublic() throws Exception {
        migrate();

        assertThat(out.resolve("public/images/logo.svg")).exists();
        assertThat(out.resolve("public/CNAME")).hasContent("hub.example.org");
        assertThat(out.resolve("public/index.html")).doesNotExist();
    }

    @Test
    void saysWhatRoqDoesOfThePluginsItDoesInPart() throws Exception {
        List<String> items = actionItems(migrate());

        assertThat(INFO).contains("Headings keep the ids that gatsby-remark-autolink-headers gave them, in the same "
                + "GitHub style, so links to them still work; the link icon it showed on hover is not migrated");
        assertThat(items).noneMatch(item -> item.contains("autolink-headers"));
    }

    @Test
    void copiesStylesInTheOrderTheSiteLoadsThem() throws Exception {
        migrate();

        assertThat(out.resolve("web/main.js")).hasContent("""
                import './_normalize.css';
                import './_style.css';
                import './_layout.css';
                """);
        assertThat(out.resolve("web/_layout.css")).hasContent("main { padding: 1em; }");
    }

    @Test
    void writesALayoutTheSiteBuildsWith() throws Exception {
        migrate();

        assertThat(out.resolve("templates/layouts/main.html")).content().contains("{#insert /}",
                "{#if p.url != site.url && !p.sourcePath.equals('Home.md')}",
                "{=page.baseFileName.replace('-', ' ')} - {=site.title}");
        assertThat(out.resolve("templates/layouts/page.html")).content().contains("layout: main",
                "{=page.data.title ?: page.baseFileName.replace('-', ' ')}");
    }

    @Test
    void removesTheGatsbyProject() throws Exception {
        migrate();

        assertThat(out.resolve("site")).doesNotExist();
        assertThat(repo.resolve("site/gatsby-config.js")).exists();
    }

    @Test
    void leavesWhatOnlyTheMarkdownRouteUsesToRoqsPageLayout() throws Exception {
        List<String> items = actionItems(migrate());

        assertThat(items).noneMatch(item -> item.contains("components/page"));
        assertThat(INFO).contains(
                "site/src/components/page.js renders the Markdown pages for {markdownRemark.fields__slug}.jsx; Roq does "
                        + "that with templates/layouts/page.html",
                "site/src/components/page-title.js renders the Markdown pages for {markdownRemark.fields__slug}.jsx; Roq "
                        + "does that with templates/layouts/page.html");
    }

    @Test
    void leavesWhatItCannotConvertAsActionItems() throws Exception {
        List<String> items = actionItems(migrate());

        assertThat(items).contains(
                "Reproduce the local Gatsby plugin site/plugins/gatsby-frontmatter-defaults/ (see its gatsby-node.js); "
                        + "it has no Roq equivalent",
                "Port the React component site/src/components/header.js to Qute in templates/",
                "Recreate the page site/src/pages/404.js as content/404.html",
                "Port the home page site/src/pages/index.jsx to templates/layouts/main.html or content/index.md",
                "Convert the CSS module site/src/components/index.module.css to plain CSS in web/; "
                        + "its class names were scoped to a component",
                "Move the styles written with styled-components into CSS in web/",
                "The 1 Jest unit tests of the React components go with them; write Roq tests (@RoqAndRoll) for "
                        + "what they covered",
                "Update .github/workflows/build.yml: run 'npm run test:int' from site-tests/, after building the site "
                        + "with Maven",
                "Update .github/workflows/wikisync.yml: it uses docs/, which moved unchanged to content/; "
                        + "point it at content/");
        assertThat(items).anyMatch(item -> item.startsWith("Update .github/workflows/build.yml: it builds the Gatsby site"));
        assertThat(items).filteredOn(item -> item.contains("npm package"))
                .containsExactly(
                        "Add the npm package prismjs (imported in site/gatsby-browser.js) as an org.mvnpm dependency, "
                                + "and import what the site uses from it in web/main.js",
                        "Add the npm package @fontsource/open-sans (imported in site/gatsby-browser.js) as an org.mvnpm "
                                + "dependency, and import what the site uses from it in web/main.js");
        assertThat(items).noneMatch(item -> item.contains("gatsby-starter-default") || item.contains("markdownRemark"));
        assertThat(out.resolve(MigrationReport.ACTION_ITEMS_FILE)).exists();
    }

    @Test
    void keepsTheTestsThatRunAgainstTheServedSite() throws Exception {
        List<String> items = actionItems(migrate());

        Path tests = out.resolve("site-tests");
        assertThat(tests.resolve("test-integration/frontpage.test.js")).exists();
        assertThat(tests.resolve("test-integration/debugenv.js")).exists();
        assertThat(tests.resolve("jest.integration.config.js")).exists();
        assertThat(tests.resolve("babel.config.js")).exists();
        assertThat(tests.resolve("package-lock.json")).exists();
        // Read by the test
        assertThat(tests.resolve("gatsby-config.js")).exists();
        // Unit tests and their configuration stay behind
        assertThat(tests.resolve("jest.config.js")).doesNotExist();
        assertThat(tests.resolve("loadershim.js")).doesNotExist();
        assertThat(tests.resolve("src")).doesNotExist();

        assertThat(items).contains("Convert the tests in site-tests/ to Java tests (@RoqAndRoll with Playwright), then "
                + "delete site-tests/; until then they run with npm, see site-tests/README.md");
    }

    @Test
    void servesTheRoqSiteWhereTheTestsExpectedGatsbys() throws Exception {
        migrate();

        Path tests = out.resolve("site-tests");
        assertThat(tests.resolve("jest-puppeteer.config.js")).content()
                .contains("command: \"node serve-roq.js 9000\"")
                .doesNotContain("gatsby serve");
        assertThat(tests.resolve("package.json")).content().contains("\"serve\": \"node serve-roq.js 9000\"");
        assertThat(tests.resolve("serve-roq.js")).content().contains("../target/roq");
        assertThat(tests.resolve("README.md")).content()
                .contains("on the port they used\nwith `gatsby serve` (9000)", "npm ci\nnpm run test:int\n```")
                .doesNotContain("npm run test\n", "${");
    }

    @Test
    void keepsAStandaloneTestWithItsConfigWhenTheGatsbyProjectIsTheRoot() throws Exception {
        repo = tmp.resolve("blog");
        write(Map.of(
                "gatsby-config.js", "module.exports = { siteMetadata: { title: \"Blog\" } }\n",
                "playwright.config.js", "module.exports = { webServer: { command: \"npx gatsby serve -p 8000\" } }\n",
                "e2e/home.spec.js", "test('home', async ({ page }) => { await page.goto('/') })\n",
                "src/pages/index.js", "export default Home\n"));

        migrate();

        assertThat(out.resolve("site-tests/e2e/home.spec.js")).exists();
        assertThat(out.resolve("site-tests/playwright.config.js")).content().contains("node serve-roq.js 8000");
        assertThat(out.resolve("e2e")).doesNotExist();
        assertThat(out.resolve("site-tests/README.md")).content().contains("(8000)", "npx playwright test");
    }

    @Test
    void keepsNothingWhenNoTestUsesTheServedSite() throws Exception {
        Files.delete(repo.resolve("site/test-integration/frontpage.test.js"));

        migrate();

        assertThat(out.resolve("site-tests")).doesNotExist();
    }

    @Test
    void reportsSourcesOutsideTheMigratedDirectory() throws Exception {
        // Migrating site/ alone leaves ../docs out of reach
        repo = repo.resolve("site");

        List<String> items = actionItems(migrate());

        assertThat(items).contains("The Gatsby source 'content' (../docs) is outside the migrated directory and was "
                + "not copied; migrate a parent directory, or copy it into content/");
    }

    @Test
    void migratesAGatsbyProjectAtTheRoot() throws Exception {
        repo = tmp.resolve("blog");
        write(Map.of(
                "gatsby-config.js", """
                        module.exports = {
                          siteMetadata: { title: "My Blog" },
                          plugins: [{ resolve: "gatsby-source-filesystem", options: { name: "blog", path: "./blog" } }],
                        }
                        """,
                "blog/index.md", "---\ntitle: Home\n---\nHello\n",
                "blog/first-post.md", "First\n",
                "src/pages/about.js", "export default About\n"));

        migrate();

        assertThat(out.resolve("content/first-post.md")).hasContent("First");
        assertThat(out.resolve("content/index.md")).content().contains("title: Home", "layout: main", "link: /");
        assertThat(out.resolve("content/index.html")).doesNotExist();
        assertThat(out.resolve("templates/layouts/main.html")).content().contains("{#if p.url != site.url}");
        assertThat(out.resolve("gatsby-config.js")).doesNotExist();
        assertThat(out.resolve("src")).doesNotExist();
    }

    @Test
    void refusesADirectoryWithoutAGatsbyProject() throws IOException {
        Path empty = Files.createDirectories(tmp.resolve("empty"));
        repo = empty;

        assertThatThrownBy(this::migrate).isInstanceOf(MigrationException.class)
                .hasMessageContaining("No gatsby-config.js");
    }

    private void write(Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = repo.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
    }
}
