---
name: quarkus-roq
description: "A static site generator (SSG) built on Quarkus. Create websites and blogs from Markdown/AsciiDoc/HTML content with FrontMatter headers, Qute templates, and data files."
guide: https://iamroq.dev/docs/
---

# Quarkus Roq

Roq is a static site generator with a Java soul and Quarkus energy. The full `quarkus-roq` extension includes `quarkus-roq-frontmatter` (template engine) plus the static site generator, CLI, themes, LLMs.txt, and testing support.

**For FrontMatter pages, layouts, collections, pagination, template variables, Qute syntax, built-in tags, data files, template extensions, configuration, and common pitfalls**, see the [quarkus-roq-frontmatter skill](https://raw.githubusercontent.com/quarkiverse/quarkus-roq/main/roq-frontmatter/deployment/src/main/resources/META-INF/quarkus-skill.md). Everything below is additional to what that skill provides.

### Directory Structure

With the full `quarkus-roq` extension, directories are at the **project root** (not `src/main/resources/` as in standalone FrontMatter):

```
content/                    # Pages and collections
  index.html                # Site index page (required)
  about.md                  # Standalone page
  posts/                    # Collection directory
    2024-08-29-my-post.md   # Date-based document
  rss.xml                   # RSS feed page
data/                       # JSON/YAML data files
  authors.yaml
  menu.json
templates/                  # Qute layout templates
  layouts/                  # Layout files
    main.html
  partials/                 # Reusable template fragments
    header.html
public/                     # Static assets served as-is
  images/                   # Default image directory
  static/                   # Static files
src/main/resources/
  web/app/                  # Web Bundler assets (CSS/JS/TS)
```

### Themes

**Theme layout resolution**: `layout: page` resolves local first (`templates/layouts/page.html`), then falls back to the theme layout (`theme-layouts/<theme-name>/page`) when `site.theme` is configured. Use `theme-layout: page` to explicitly target the theme layout. The base theme (`roq-base`, the default) provides: `default`, `page`, `post`. The full default theme (`roq-default`, installed via `roq create`) provides: `default`, `main`, `post`, `page`, `index`, `blog`, `home`, `tag`, `404`. Override any theme layout by placing a file at `templates/layouts/<layout>.html` with `theme-layout: <layout>` in frontmatter to inherit from the theme version.

### RSS Feed

Create `content/rss.xml`:
```html
---
---
{#include fm/rss.html /}
```

Add `{#rss site /}` to your root layout `<head>` to include the RSS feed link.

Use `contentLimit` to control `<content:encoded>`: omit for description only (default), `contentLimit=0` for full rendered content, `contentLimit=N` for a word-limited content abstract (e.g. `{#include fm/rss.html contentLimit=150 /}`).

### LLMs.txt

Generate `/llms.txt` and `/llms-full.txt` for AI discoverability. Create `content/llms.qute.txt`:
```
{#include fm/llms.html}
```
And `content/llms-full.qute.txt`:
```
{#include fm/llms-full.html}
```
Exclude pages with `llmstxt: false` in frontmatter.

### Static Generation

Build and export as a static site:
```bash
roq generate
```

Output goes to `target/roq/`. Preview with:
```bash
roq serve
```

**Key configuration** (`application.properties`):
```properties
quarkus.roq.generator.batch=true
quarkus.roq.generator.output-dir=roq
quarkus.roq.generator.paths=/,/static/**
quarkus.roq.generator.timeout=60
```

For GitHub Pages deployment, use the Roq GitHub Action or configure your CI to build with `quarkus.roq.generator.batch=true` and deploy the `target/roq/` directory.

### Testing

```java
@QuarkusTest
@RoqAndRoll
@TestProfile(MyProfile.class)
public class MySiteTest {

    @Test
    public void testHomePage() {
        RestAssured.when().get("/")
            .then().statusCode(200)
            .body(containsString("Welcome"));
    }

    @Test
    public void testBlogPost() {
        RestAssured.when().get("/posts/my-post")
            .then().statusCode(200)
            .body("html.head.title", containsString("My Post"));
    }

    public static class MyProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() { return "roq-and-roll"; }
    }
}
```

Uses `@RoqAndRoll(port=8082)` from the `roq-testing` module with REST Assured for HTTP assertions.

### CLI Commands (complete list, there is NO `roq dev` command)

- `roq create my-site` — create a new site with the default theme. Options: `-x theme:base` for minimal theme, `-x plugin:tagging,plugin:series` to add plugins, `--no-code` to skip example content, `--gradle` for Gradle, `-g io.myorg` to set group ID
- `roq start` — start dev mode with live-reload on http://localhost:8080. Use `-p 9090` to set a custom port
- `roq generate` — build static site to `target/roq/`
- `roq serve` — preview generated site (default port 8181, use `-p` to change)
- `roq add plugin:tagging` — add a plugin or theme
- `roq update` — update the Roq/Quarkus project to latest versions
- `roq blog` — list blog posts from a Roq site RSS feed
