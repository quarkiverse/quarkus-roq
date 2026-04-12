---
name: quarkus-roq-frontmatter
description: "Create a website from your Markdown/Asciidoc/Html pages using FrontMatter headers (url, layout, seo, data). Can be used standalone with a running Quarkus app (hybrid mode, SSR) or as part of the full Roq SSG."
guide: https://docs.quarkiverse.io/quarkus-roq/dev/quarkus-roq-frontmatter.html
---

# Quarkus Roq FrontMatter

Roq FrontMatter processes Markdown, AsciiDoc, and HTML pages with YAML frontmatter headers into web pages using Qute templates. It can be used standalone with a running Quarkus app (hybrid mode, SSR) or as part of the full Roq static site generator.

### Directory Structure

When used standalone (without the full `quarkus-roq` extension), directories are under `src/main/resources/`:

```
src/main/resources/
  content/                  # Pages and collections
    index.html              # Site index page (required)
    about.md                # Standalone page
    posts/                  # Collection directory
      2024-08-29-my-post.md # Date-based document
  data/                     # JSON/YAML data files
  templates/                # Qute layout templates
    layouts/                # Layout files
      default.html
      main.html
    partials/               # Reusable template fragments
      header.html
  public/                   # Static assets served as-is
    images/                 # Default image directory
```

### FrontMatter Pages

Pages in `content/` use YAML frontmatter between `---` delimiters. Supported formats: `.md` (Markdown), `.html`, `.adoc` (AsciiDoc).

```yaml
---
title: "My Page Title"
description: "Page description for SEO"
layout: main
image: photo.jpg
date: 2024-08-29 13:32:20 +0200
tags: [blogging, quarkus]
author: ia3andy
draft: false
paginate:
  collection: posts
  size: 10
  link: posts/page-:page
---
Page content here (Markdown, HTML, or AsciiDoc)
```

Key fields:
- **title** — Page title (falls back to source path if missing)
- **description** — Used for SEO meta tags
- **layout** — Qute layout template to wrap this page. References layouts in `templates/layouts/` (e.g. `main`, `default`)
- **image** — Page image. Can be a full URL, a filename from `public/images/`, or an attached file name
- **date** — Page date. For collection documents, can also be parsed from filename (`YYYY-MM-DD-slug.md`)
- **tags** — String or array of tags
- **author** — Author identifier
- **draft** — `true` to mark as draft (hidden unless `site.draft=true`)
- **paginate** — Enable pagination. Shorthand: `paginate: posts`. Full config: `collection`, `size`, `link`

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
  {#insert head /}
</head>
<body>
  {#insert /}
</body>
</html>
```

**Layout chain**: A page with `layout: post` → post layout has `layout: main` → main has `layout: default` → default is the root (no layout field). Each level uses `{#insert /}` where child content goes.

**IMPORTANT**: Use `{#insert /}` in layouts to render child content. NEVER use `{page.content}` in layouts — that causes recursive rendering issues.

### Collections

Collections group related documents (e.g. blog posts). The default collection is `posts`.

**Configuration** (`application.properties`):
```properties
site.collections.posts.layout=post
site.collections.posts.future=false
site.collections.posts.hidden=false

# Custom collection
site.collections.recipes.layout=page
site.collections.recipes.hidden=false
```

**File naming**: Documents in collection directories use date-based names: `YYYY-MM-DD-slug.md` (e.g. `2024-08-29-welcome.md`). The date is extracted from the filename.

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

{! Type declarations (in layouts) !}
{@io.quarkiverse.roq.frontmatter.runtime.model.Page page}
{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}
{@io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage page}

{! Escaping Qute in content !}
\{not-a-qute-expression}
```

### Hybrid Mode

Roq FrontMatter works alongside a running Quarkus app — pages are server-rendered by the Quarkus HTTP server. There is no static site generation (that requires the `quarkus-roq-generator` extension).

**Key points**:
- Pages are served dynamically by Quarkus, enabling SSR alongside REST endpoints, WebSockets, etc.
- Combine with `quarkus-web-bundler` for JS/TS/CSS bundling using `{#bundle /}` in layouts
- Use `site.path-prefix` to serve Roq pages under a sub-path when coexisting with other Quarkus routes

**Configuration** (`application.properties`):
```properties
site.url=http://localhost:8080
site.path-prefix=/blog
```

### Qute Reference Guide

For advanced Qute template needs beyond the essentials above (e.g. `@TemplateExtension` to add custom methods to objects, type-safe templates, template globals, or complex expressions), consult the full Qute reference guide: https://quarkus.io/guides/qute-reference

### Common Pitfalls

- **Layout inheritance** — ALWAYS use the frontmatter `layout:` field for layout inheritance (e.g. `layout: main`). NEVER use Qute's `{#include}` or `{#layout}` directives for this purpose — they won't work with Roq's layout chain.
- **`{page.content}` in layouts** — NEVER use `{page.content}` in layouts. Use `{#insert /}` to render child content. `{page.content}` causes recursive rendering.
- **Wrong directory location** — Standalone Roq FrontMatter uses `src/main/resources/` for `content/`, `templates/`, `public/`, `data/`. NOT project root (project root is only for the full `quarkus-roq` extension).
- **Layout resolution** — `layout: page` resolves local first (`templates/layouts/page.html`), then theme fallback. Use `theme-layout: page` to explicitly target the theme layout. The old `:theme/` prefix syntax is deprecated.
- **Date format in filenames** — Collection documents must use `YYYY-MM-DD-slug.md` format for date extraction.
- **Image resolution** — Images can be: a full URL (`https://...`), a filename resolved from `public/images/`, or an attached file name for directory-based pages.
- **Escaping Qute** — Use `\{expression}` to escape Qute expressions in content that should be rendered literally.
