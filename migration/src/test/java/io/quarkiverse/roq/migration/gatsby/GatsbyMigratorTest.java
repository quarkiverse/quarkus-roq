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
import io.quarkiverse.roq.migration.engine.ProjectDependency;

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
                Map.entry("site/package-lock.json", """
                        {
                          "lockfileVersion": 3,
                          "packages": {
                            "node_modules/@fontsource/open-sans": {"version": "5.0.29"},
                            "node_modules/@fortawesome/fontawesome-svg-core": {"version": "6.6.0"},
                            "node_modules/prismjs": {"version": "1.29.0"}
                          }
                        }
                        """),
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
                Map.entry("site/src/components/layout.js", """
                        import * as React from "react"
                        import {graphql, useStaticQuery} from "gatsby"
                        import Header from "./header"
                        import Navigation from "./navigation"
                        import Footer from "./footer"

                        const Layout = ({children, pageName}) => {
                            const data = useStaticQuery(graphql`
                              query { site { siteMetadata { title } } }
                            `)
                            return (
                                <>
                                    <Header siteTitle={data.site.siteMetadata?.title || `Title`} pageName={pageName}/>
                                    <div style={{display: 'flex', columnGap: '40px'}}>
                                        <Navigation/>
                                        <main>{children}</main>
                                    </div>
                                    <Footer/>
                                </>
                            )
                        }

                        export default Layout
                        """),
                Map.entry("site/src/components/header.js", """
                        import * as React from "react"
                        import {Link} from "gatsby"
                        import {StaticImage} from "gatsby-plugin-image"
                        import styled from "styled-components"
                        import {FontAwesomeIcon} from '@fortawesome/react-fontawesome'
                        import {faPencil} from '@fortawesome/free-solid-svg-icons'

                        // A comment that mentions onClick={...} does not make it interactive
                        const Logo = styled(props => <a {...props} />)`
                          display: flex;
                          &:hover {
                            opacity: 0.8;
                          }
                        `

                        const Header = ({siteTitle, pageName}) => (
                            <header style={{margin: `0 auto`, padding: 24}}>
                                <Logo href="https://www.quarkiverse.io/">
                                    <StaticImage src="../images/logo.svg" alt="Logo" height={36} placeholder="none"/>
                                </Logo>
                                <Link to="/" className="title"><h1>{siteTitle}</h1></Link>
                                {pageName && (
                                    <a href={`https://github.com/example/edit/main/docs/${pageName}.md`}>
                                        <FontAwesomeIcon icon={faPencil}/>
                                        Edit this Page
                                    </a>
                                )}
                            </header>
                        )

                        export default Header
                        """),
                Map.entry("site/src/components/footer.js", """
                        import * as React from "react"

                        const Footer = () => {
                            return (
                                <footer className="navigation">
                                    Sponsored by <a href="https://www.redhat.com/">Red Hat</a>
                                </footer>
                            )
                        }

                        export default Footer
                        """),
                Map.entry("site/src/components/navigation.js",
                        """
                                import * as React from 'react'
                                import {graphql, Link, useStaticQuery} from 'gatsby'

                                const Navigation = () => {
                                    const data = useStaticQuery(graphql`
                                      query { allMarkdownRemark { edges { node { fields { slug title } } } } }
                                    `)
                                    return (
                                        <ul>
                                            {data.allMarkdownRemark.edges.map((edge, i) => edge.node.fields.slug.startsWith("_") || (
                                                <li key={i}><Link to={"/" + edge.node.fields.slug}>{edge.node.fields.title}</Link></li>
                                            ))}
                                        </ul>
                                    )
                                }

                                export default Navigation
                                """),
                Map.entry("site/src/components/search.js", """
                        import * as React from "react"
                        import {useState} from "react"

                        export default function Search() {
                            const [query, setQuery] = useState("")
                            return <input value={query} onChange={e => setQuery(e.target.value)}/>
                        }
                        """),
                Map.entry("site/src/components/stars.js", """
                        import * as React from "react"
                        import {useEffect, useState} from "react"

                        export default function Stars() {
                            const [stars, setStars] = useState(0)
                            useEffect(() => {
                                fetch("https://api.github.com/repos/quarkiverse/quarkiverse").then(r => r.json())
                                    .then(repo => setStars(repo.stargazers_count))
                            }, [])
                            return <span>{stars}</span>
                        }
                        """),
                Map.entry("site/src/components/hero.js", "import * as styles from \"./hero.module.css\";\n"),
                Map.entry("site/src/components/hero.module.css", ".hero { color: blue; }\n"),
                Map.entry("site/src/components/header.test.js", "it('renders', () => {})\n"),
                Map.entry("site/src/images/logo.svg", "<svg/>"),
                Map.entry("site/src/pages/index.jsx", """
                        import * as React from 'react'
                        import Layout from "../components/layout"
                        const HomePage = () => <Layout><p>Home</p></Layout>
                        export default HomePage
                        """),
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
                Map.entry("site/src/pages/404.js", """
                        import * as React from "react"
                        import Layout from "../components/layout"
                        const NotFoundPage = () => <Layout><h1>404: Not Found</h1></Layout>
                        export default NotFoundPage
                        """),
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

    /**
     * Shaped like the pom.xml roq create writes: managed dependencies first, then the project's own.
     */
    static final String POM = """
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>io.quarkus.platform</groupId>
                            <artifactId>quarkus-bom</artifactId>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>io.quarkiverse.roq</groupId>
                        <artifactId>quarkus-roq</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """;

    MigrationResult migrate() throws MigrationException {
        return new MigrationRunner(new GatsbyMigrator(), repo, site -> Files.writeString(site.dir().resolve("pom.xml"), POM),
                site -> {
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
    void asksToResizeOnlyTheLargeImages() throws Exception {
        write(Map.of("site/gatsby-config.js", GatsbyConfigTest.CONFIG.replace("`gatsby-plugin-image`,",
                "`gatsby-plugin-image`,\n`gatsby-plugin-sharp`,\n`gatsby-transformer-sharp`,")));
        assertThat(actionItems(migrate())).noneMatch(item -> item.contains("Resize"));

        io.quarkiverse.roq.migration.SiteFiles.deleteTree(out);
        Files.write(repo.resolve("site/src/images/photo.jpg"), new byte[1200 * 1024]);
        assertThat(actionItems(migrate())).contains("Resize the large images in public/, which gatsby-plugin-sharp and "
                + "gatsby-transformer-sharp resized and Roq does not: images/photo.jpg (1.2 MB)");
    }

    @Test
    void saysWhatRoqDoesOfThePluginsItDoesInPart() throws Exception {
        List<String> items = actionItems(migrate());

        assertThat(INFO).contains("Headings keep the ids that gatsby-remark-autolink-headers gave them, in the same "
                + "GitHub style, so links to them still work; the link icon it showed on hover is not migrated");
        assertThat(items).noneMatch(item -> item.contains("autolink-headers"));
    }

    @Test
    void importsStylesInTheOrderTheSiteLoadsThem() throws Exception {
        migrate();

        // gatsby-browser.js order, local and npm imports mixed, then component styles
        assertThat(out.resolve("web/main.js")).hasContent("""
                import './_normalize.css';
                import './_style.css';
                import 'prismjs/themes/prism.css';
                import '@fontsource/open-sans';
                import '@fontsource/open-sans/300.css';
                import './_layout.css';
                import '@fortawesome/fontawesome-free/css/all.min.css';
                import './_components.css';
                """);
        assertThat(out.resolve("web/_layout.css")).hasContent("main { padding: 1em; }");
        assertThat(out.resolve("web/_hero.module.css")).doesNotExist();
    }

    @Test
    void asksForTheImportedNpmPackagesAsMvnpmDependencies() throws Exception {
        MigrationResult result = migrate();

        // The scaffolder adds them; the migrator does not edit the build file
        assertThat(result.analysis().dependencies()).containsExactly(
                new ProjectDependency("org.mvnpm", "prismjs", "1.29.0", "provided"),
                new ProjectDependency("org.mvnpm.at.fontsource", "open-sans", "5.0.29", "provided"),
                new ProjectDependency("org.mvnpm.at.fortawesome", "fontawesome-free", "6.6.0", "provided"));
        assertThat(out.resolve("pom.xml")).hasContent(POM);
    }

    @Test
    void writesALayoutTheSiteBuildsWith() throws Exception {
        migrate();

        assertThat(out.resolve("templates/layouts/main.html")).content().contains("{#seo page site /}", "{#bundle /}",
                "{=page.baseFileName.replace('-', ' ')} - {=site.title}");
        assertThat(out.resolve("templates/layouts/page.html")).content().contains("layout: main",
                "{=page.data.title ?: page.baseFileName.replace('-', ' ')}");
    }

    @Test
    void convertsTheRootLayoutToTheMainLayout() throws Exception {
        List<String> items = actionItems(migrate());

        assertThat(out.resolve("templates/layouts/main.html")).content().contains("""
                <body>
                  {! Converted from site/src/components/layout.js by the migration to Roq; parameters: pageName !}
                  {#include partials/header siteTitle=(site.title ?: 'Title') pageName=pageName?? /}
                  <div style="display: flex; column-gap: 40px">
                    {#include partials/navigation /}
                    <main>{#insert /}</main>
                  </div>
                  {#include partials/footer /}
                </body>
                """);
        assertThat(out.resolve("templates/partials/layout.html")).doesNotExist();
        assertThat(items).contains("Give pageName a value in templates/layouts/main.html, for example from page with "
                + "{#let}: the pages passed it to site/src/components/layout.js, and a Roq layout only has page and site");
        assertThat(items).noneMatch(item -> item.contains("layout.js to Qute") || item.startsWith("Pass "));
    }

    @Test
    void listsThePagesAStaticQueryListed() throws Exception {
        List<String> items = actionItems(migrate());

        // The route {markdownRemark.fields__slug} serves the pages at fields.slug
        assertThat(out.resolve("templates/partials/navigation.html")).hasContent("""
                {! Converted from site/src/components/navigation.js by the migration to Roq !}
                <ul>
                  {#for edge in site.pages}
                    {#if edge.source.markup == 'markdown'}
                      {#if !edge.baseFileName.slugify(true).startsWith('_')}
                        <li><a href="{=edge.url}">{=edge.data.title ?: edge.baseFileName.replace('-', ' ')}</a></li>
                      {/if}
                    {/if}
                  {/for}
                </ul>
                """);
        assertThat(items).contains("Check the titles in templates/partials/navigation.html: "
                + "site/src/components/navigation.js used fields.title, which the site's gatsby-node.js or a plugin "
                + "set; the template uses the front matter title, or else the file name with dashes as spaces");
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
    void removesTheGatsbyProject() throws Exception {
        migrate();

        assertThat(out.resolve("site")).doesNotExist();
        assertThat(repo.resolve("site/gatsby-config.js")).exists();
    }

    @Test
    void leavesWhatItCannotConvertAsActionItems() throws Exception {
        List<String> items = actionItems(migrate());

        assertThat(items).contains(
                "Reproduce the local Gatsby plugin site/plugins/gatsby-frontmatter-defaults/ (see its gatsby-node.js); "
                        + "it has no Roq equivalent",
                "Recreate the page site/src/pages/404.js as content/404.html; it is a static template",
                "Port the home page site/src/pages/index.jsx to templates/layouts/main.html or content/index.md; "
                        + "it is a static template",
                "Convert the CSS module site/src/components/hero.module.css to plain CSS in web/; "
                        + "its class names were scoped to a component",
                "The 1 Jest unit tests of the React components go with them; write Roq tests (@RoqAndRoll) for "
                        + "what they covered",
                "Update .github/workflows/build.yml: run 'npm run test:int' from site-tests/, after building the site "
                        + "with Maven",
                "Update .github/workflows/wikisync.yml: it uses docs/, which moved unchanged to content/; "
                        + "point it at content/");
        assertThat(items).anyMatch(item -> item.startsWith("Update .github/workflows/build.yml: it builds the Gatsby site"));
        assertThat(items).noneMatch(item -> item.contains("npm package") || item.contains("mvnpm"));
        // Nothing imports it
        assertThat(items).noneMatch(item -> item.contains("index.module.css"));
        assertThat(items).noneMatch(item -> item.contains("gatsby-starter-default") || item.contains("markdownRemark"));
        assertThat(out.resolve(MigrationReport.ACTION_ITEMS_FILE)).exists();
    }

    @Test
    void convertsStaticComponentsToQutePartials() throws Exception {
        migrate();

        assertThat(out.resolve("templates/partials/header.html")).hasContent("""
                {! Converted from site/src/components/header.js by the migration to Roq; parameters: siteTitle, pageName !}
                <header style="margin: 0 auto; padding: 24px">
                  <a href="https://www.quarkiverse.io/" class="header-logo"><img src="{=site.url('images/logo.svg')}" \
                alt="Logo" height="36" style="margin: 0; object-fit: cover"></a>
                  <a href="/" class="title">
                    <h1>{=siteTitle??}</h1>
                  </a>
                  {#if pageName??}<a href="https://github.com/example/edit/main/docs/{=pageName??}.md">\
                <i class="fa-solid fa-pencil" aria-hidden="true"></i>Edit this Page</a>{/if}
                </header>
                """);
        assertThat(out.resolve("templates/partials/footer.html")).hasContent("""
                {! Converted from site/src/components/footer.js by the migration to Roq !}
                <footer class="navigation">Sponsored by <a href="https://www.redhat.com/">Red Hat</a></footer>
                """);
    }

    @Test
    void rendersTheConvertedComponentsWhereTheRootLayoutDidWhenItIsNotConverted() throws Exception {
        write(Map.of("site/src/components/navigation.js", """
                import * as React from 'react'
                import {useState} from 'react'
                const Navigation = () => {
                    const [open, setOpen] = useState(false)
                    return <ul onClick={() => setOpen(!open)}/>
                }
                export default Navigation
                """));
        List<String> items = actionItems(migrate());

        assertThat(out.resolve("templates/layouts/main.html")).content()
                .contains(
                        "<body>\n  {#include partials/header siteTitle=(site.title ?: 'Title') /}\n  <div class=\"site-body\">")
                .contains("</div>\n  {#include partials/footer /}\n</body>")
                .contains("{#if p.url != site.url && !p.sourcePath.equals('Home.md')}")
                .doesNotContain("<header>");
        assertThat(items).contains("Pass pageName to partials/header in templates/layouts/main.html: "
                + "site/src/components/layout.js passes it from its own props, which have no Roq equivalent");
    }

    @Test
    void movesTheConvertedStyledComponentsToCss() throws Exception {
        MigrationResult result = migrate();

        assertThat(out.resolve("web/_components.css")).hasContent("""
                /* site/src/components/header.js */
                .header-logo {
                  display: flex;
                  &:hover {
                    opacity: 0.8;
                  }
                }
                """);
        assertThat(out.resolve("web/main.js")).content()
                .endsWith("import '@fortawesome/fontawesome-free/css/all.min.css';\nimport './_components.css';\n");
        // For the converted icons
        assertThat(result.analysis().dependencies())
                .contains(new ProjectDependency("org.mvnpm.at.fortawesome", "fontawesome-free", "6.6.0", "provided"));
    }

    @Test
    void saysWhyEachComponentWasNotConverted() throws Exception {
        List<String> items = actionItems(migrate());

        assertThat(items).contains(
                "Keep the React component site/src/components/search.js as React, or rewrite it: it is interactive, "
                        + "with event handlers (onChange), state (useState), which a Qute template is not",
                "Move what the React component site/src/components/stars.js computes in the browser (state set from "
                        + "useEffect, fetch) to build time, with Roq data or Qute, then port it to Qute in templates/");
        // Converted, and used by the layout
        assertThat(items).noneMatch(item -> item.contains("header.js") || item.contains("footer.js"));
        // Its only StaticImage was converted
        assertThat(items).noneMatch(item -> item.contains("<StaticImage>"));
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
