---
name: quarkus-roq
description: "A static site generator (SSG) built on Quarkus. Create websites and blogs from Markdown/AsciiDoc/HTML content with FrontMatter headers, Qute templates, and data files."
guide: https://iamroq.com/docs/
---

# Quarkus Roq

Roq is a static site generator with a Java soul and Quarkus energy. It processes content files with FrontMatter headers, Qute templates, and data files to produce static websites. The full `quarkus-roq` extension includes the generator for static export.

### Directory Structure

Roq sites use the **project root** as the site directory (not `src/main/resources/`):

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

### FrontMatter Pages

Pages in `content/` use YAML frontmatter between `---` delimiters. Supported formats: `.md` (Markdown), `.html`, `.adoc` (AsciiDoc).

```yaml
---
title: "My Page Title"
description: "Page description for SEO"
layout: page
image: photo.jpg
date: 2024-08-29 13:32:20 +0200
tags: [blogging, quarkus]
author: ia3andy
draft: false
paginate:
  collection: posts
  size: 10
  link: posts/page-:page
redirect_from: [old-url, another-old-url]
---
Page content here (Markdown, HTML, or AsciiDoc)
```

Key fields:
- **title** — Page title (falls back to source path if missing)
- **description** — Used for SEO meta tags
- **layout** — Qute layout template to wrap this page. Resolves local first, then theme fallback (e.g. `page`, `post`). Use `theme-layout:` to explicitly target a theme layout
- **image** — Page image. Can be a full URL, a filename from `public/images/`, or an attached file name (for directory pages)
- **date** — Page date. For collection documents, can also be parsed from filename (`YYYY-MM-DD-slug.md`)
- **tags** — String or array of tags
- **author** — Author identifier
- **draft** — `true` to mark as draft (hidden unless `site.draft=true`)
- **paginate** — Enable pagination. Shorthand: `paginate: posts`. Full config: `collection`, `size`, `link`
- **redirect_from** / **aliases** — Old URLs that redirect to this page (requires aliases plugin)

### Layouts

Layouts are Qute templates in `templates/layouts/` that wrap page content using `{#insert /}`.

**Layout file** (`templates/layouts/main.html`):
```html
---
layout: default
---
{@io.quarkiverse.roq.frontmatter.runtime.model.Page page}
{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}
<main>
  <h1>{page.title}</h1>
  {#insert /}
</main>
```

**Root layout** (`templates/layouts/default.html`):
```html
{@io.quarkiverse.roq.frontmatter.runtime.model.Page page}
{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}
<!DOCTYPE html>
<html>
<head>
  <title>{page.title}</title>
  {#seo page site /}
  {#rss site /}
  {#insert head /}
</head>
<body>
  {#insert /}
</body>
</html>
```

**Layout chain**: A page with `layout: post` → post layout has `layout: main` → main has `layout: default` → default is the root (no layout field). Each level uses `{#insert /}` where child content goes.

**Theme layout resolution**: `layout: page` resolves local first (`templates/layouts/page.html`), then falls back to the theme layout (`theme-layouts/<theme-name>/page`) when `site.theme` is configured. Use `theme-layout: page` to explicitly target the theme layout. The default theme (`roq-default`) provides: `default`, `main`, `post`, `page`, `index`, `tag`, `404`. Override any theme layout by placing a file at `templates/layouts/<layout>.html` with `theme-layout: <layout>` in frontmatter to inherit from the theme version.

**IMPORTANT**: Use `{#insert /}` in layouts to render child content. NEVER use `{page.content}` in layouts — that causes recursive rendering issues.

### Collections

Collections group related documents (e.g. blog posts). The default collection is `posts` with layout `post`.

**Configuration** (`application.properties`):
```properties
site.collections.posts.layout=post
site.collections.posts.future=false
site.collections.posts.hidden=false

# Custom collection
site.collections.recipes.layout=page
site.collections.recipes.hidden=false
```

**File naming**: Documents in collection directories use date-based names: `YYYY-MM-DD-slug.md` (e.g. `2024-08-29-welcome-to-roq.md`). The date is extracted from the filename.

**Iterating**:
```html
{#for post in site.collections.posts}
  <a href="{post.url}">{post.title}</a>
{/for}
```

**Document navigation**:
```html
{#if page.previous}
  <a href="{page.previous.url}">Previous: {page.previous.title}</a>
{/if}
{#if page.next}
  <a href="{page.next.url}">Next: {page.next.title}</a>
{/if}
```

### Pagination

Enable pagination in a page's frontmatter:

```yaml
---
paginate: posts
# Or full config:
paginate:
  collection: posts
  size: 10
  link: posts/page-:page
---
```

**Iterating paginated documents**:
```html
{#for post in site.collections.posts.paginated(page.paginator)}
  <article>
    <h2><a href="{post.url}">{post.title}</a></h2>
    <p>{post.description}</p>
  </article>
{/for}
```

**Pagination controls** (built-in partial):
```html
{#include fm/pagination.html}
  {#newer}<i class="fa fa-arrow-left"></i>{/newer}
  {#older}<i class="fa fa-arrow-right"></i>{/older}
{/include}
```

**Paginator properties** (available as `page.paginator`):
- `paginator.collection` — collection name
- `paginator.collectionSize` — total documents
- `paginator.limit` — docs per page
- `paginator.total` — total number of pages
- `paginator.currentIndex` — current page (1-based)
- `paginator.firstUrl` — URL of first page
- `paginator.previous` / `paginator.prev` — previous page URL (null if first)
- `paginator.next` — next page URL (null if last)
- `paginator.pagesUrl` — list of all page URLs
- `paginator.isFirst` / `paginator.isSecond` — boolean checks

### Template Variables

**`site` object** (`Site`):
- `site.url` — site root URL (`RoqUrl`)
- `site.url(path)` — resolve path relative to root. Also `site.url(path, path1)`, `site.url(path, path1, path2)`
- `site.title` — from index page frontmatter `title`
- `site.description` — from index page frontmatter `description`
- `site.image` — default site image from frontmatter `image`/`img`/`picture`
- `site.image(name)` — resolve image from `public/images/`
- `site.imageExists(name)` — check if image exists
- `site.data` — site-level frontmatter data (`JsonObject`)
- `site.pages` — all normal pages (no collection documents)
- `site.collections` — all collections (e.g. `site.collections.posts`)
- `site.allPages` — all pages including documents
- `site.index` — the site index page
- `site.page(sourcePath)` — find page by source path
- `site.file(name)` — resolve file from `public/`
- `site.fileExists(name)` — check if public file exists
- `site.files` — list of all public static files
- `site.pageContent(page)` — render a page's inner content

**`page` object** (`Page`):
- `page.url` — page URL (`RoqUrl`)
- `page.title` — from frontmatter `title`
- `page.description` — from frontmatter `description`
- `page.image` — page image (`RoqUrl`)
- `page.image(name)` — resolve specific image
- `page.imageExists(name)` — check if image exists
- `page.date` — page date (`ZonedDateTime`, null for normal pages without a date; always set for collection documents)
- `page.data` — all frontmatter data (`JsonObject`)
- `page.data(name)` — get specific frontmatter value
- `page.rawContent` — raw content without frontmatter or layouts
- `page.sourcePath` — source file relative path (e.g. `posts/my-post.md`)
- `page.sourceFileName` — file name only
- `page.baseFileName` — file name without extension
- `page.id` — unique identifier (source path)
- `page.draft` — whether page is a draft
- `page.files` — attached files (directory pages only)
- `page.file(name)` — resolve attached file URL
- `page.fileExists(name)` — check if attached file exists
- `page.site` — reference back to `Site`

**`page` for collection documents** (`DocumentPage` extends `Page`):
- `page.collectionId` — collection name
- `page.collection` — the `RoqCollection`
- `page.next` / `page.nextPage` — next document in collection
- `page.previous` / `page.prev` / `page.previousPage` / `page.prevPage` — previous document
- `page.hidden` — whether document is hidden

**`RoqUrl`** (URL object):
- `{url}` or `{url.path}` — relative path (e.g. `/posts/my-post/`), encoded
- `{url.absolute}` — full URL (e.g. `https://example.com/posts/my-post/`)
- `{url.relative}` — same as `path`
- `{url.encoded}` — URL-encoded absolute URL
- `{url.resolve(path)}` or `{url.join(path)}` — join with another path
- `{url.append(str)}` — concatenate without `/`
- `{url.isExternal}` — boolean
- `{url.fromRoot(path)}` — resolve from app root

### Qute Syntax Essentials

```html
{! Expressions !}
{page.title}
{page.data.customField}
{page.date.format('yyyy, MMM dd')}

{! Conditionals !}
{#if page.image}...{/if}
{#if page.data.author??}...{/if}

{! Loops !}
{#for post in site.collections.posts}...{/for}
{#for tag in page.data.tags.asStrings}...{/for}

{! Include partial !}
{#include partials/header /}

{! Layout insertion point (in layouts) !}
{#insert /}
{#insert head /}
{#insert menu}{#include partials/sidebar-menu /}{/}

{! URL resolution !}
<a href="{site.url('posts/my-post')}">Link</a>
<a href="{site.url('docs', 'getting-started')}">Docs</a>

{! CDI beans (data files) !}
{cdi:authors.ia3andy.name}
{#for item in cdi:contributors.contributors}{item.name}{/for}

{! Let bindings !}
{#let author=cdi:authors.get(page.data.author)}
  {author.name}
{/let}

{! Slugify !}
{#let tagSlug=tag.slugify}
  <a href="{site.url('/posts/tag', tagSlug)}">{tagSlug}</a>
{/let}

{! Type declarations (in layouts) !}
{@io.quarkiverse.roq.frontmatter.runtime.model.Page page}
{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}
{@io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage page}

{! Escaping Qute in content !}
\{not-a-qute-expression}
```

### Data Files

Place JSON or YAML files in the `data/` directory. Each file becomes a CDI bean accessible in templates.

**Example** (`data/authors.yaml`):
```yaml
ia3andy:
  name: Andy
  url: https://github.com/ia3andy
```

**Template access**:
```html
{cdi:authors.ia3andy.name}

{#let author=cdi:authors.get(page.data.author)}
  <span>{author.name}</span>
{/let}
```

**Type-safe mapping** (Java):
```java
@DataMapping("authors")
public record Authors(Map<String, Author> authors) {
    public record Author(String name, String url) {}
}
```

### SEO & RSS

Add to root layout `<head>`:
```html
{#seo page site /}
{#rss site /}
```

For RSS feed, create `content/rss.xml`:
```html
---
---
{#include fm/rss.html /}
```

### Static Generation

Build and export as a static site:
```bash
quarkus build -Dquarkus.roq.generator.batch
```

Output goes to `target/roq/`. Preview with:
```bash
quarkus run
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

### Qute Reference Guide

For advanced Qute template needs beyond the essentials above (e.g. `@TemplateExtension` to add custom methods to objects, type-safe templates, template globals, or complex expressions), consult the full Qute reference guide: https://quarkus.io/guides/qute-reference

### Common Pitfalls

- **Layout inheritance** — ALWAYS use the frontmatter `layout:` field for layout inheritance (e.g. `layout: main`). NEVER use Qute's `{#include}` or `{#layout}` directives for this purpose — they won't work with Roq's layout chain.
- **`{page.content}` in layouts** — NEVER use `{page.content}` in layouts. Use `{#insert /}` to render child content. `{page.content}` causes recursive rendering.
- **Wrong directory location** — Roq sites use **project root** for `content/`, `templates/`, `public/`, `data/`. NOT `src/main/resources/`.
- **Layout resolution** — `layout: page` resolves local first, then theme fallback. Use `theme-layout: page` for explicit theme targeting. The old `:theme/` prefix syntax is deprecated.
- **Date format in filenames** — Collection documents must use `YYYY-MM-DD-slug.md` format for date extraction.
- **Image resolution** — Images can be: a full URL (`https://...`), a filename resolved from `public/images/`, or an attached file name for directory-based pages. The `image` frontmatter field handles all three.
- **Escaping Qute** — Use `\{expression}` to escape Qute expressions in content that should be rendered literally. Add pages to `site.escaped-pages` config to skip Qute parsing entirely.
