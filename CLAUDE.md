# Roq Dev Guide

**Remind me to commit when changes are consequent enough**


# Roq Blog Development Guide

Blog is in blog/

## Project Structure

### Overview
Roq is a Quarkus-based static site generator organized as a multi-module Maven project:

```
quarkus-roq/
├── blog/                    # Example blog site (development/testing)
├── docs/                    # Project documentation
├── roq/                     # Core Roq extension
├── roq-common/              # Common utilities
├── roq-data/                # Data handling
├── roq-editor/              # Live preview editor
├── roq-frontmatter/         # Front matter processing (step-based pipeline, see PIPELINE.md)
├── roq-generator/           # Static site generation engine
├── roq-plugin/              # Plugin modules (see below)
├── roq-testing/             # Testing utilities
└── roq-theme/               # Theme modules (see below)
```

### Quarkus Extension Pattern
Most modules follow the standard Quarkus extension structure:
- `runtime/` - Runtime classes (included in the application)
- `deployment/` - Build-time classes (Quarkus build steps)
- `integration-tests/` - Integration tests (optional)

### Blog Site (`blog/`)
The example blog demonstrates Roq features and serves as a development environment:

```
blog/
├── content/              # Site content
│   ├── docs/            # Documentation pages (AsciiDoc)
│   ├── posts/           # Blog posts (Markdown/AsciiDoc)
│   └── markups/         # Markup testing pages (not indexed)
├── templates/           # Custom templates/layouts
│   ├── layouts/         # Page layouts (flat, no theme subdirs)
│   └── partials/        # Reusable template fragments
├── data/                # Static data files (YAML/JSON)
├── includes/            # AsciiDoc includes
├── public/              # Static assets (images, etc.)
├── web/                 # Frontend assets (JS/CSS)
├── config/              # Configuration files
└── src/                 # Custom Java code (optional)
```

### Themes (`roq-theme/`)
Themes are Quarkus extensions providing layouts and styles:
- `default/` - Default Roq theme (Tailwind-based)
- `resume/` - Resume/CV theme

**Theme structure:**
```
roq-theme/default/runtime/src/main/resources/
├── templates/
│   ├── theme-layouts/roq-default/  # Theme layouts (resolved via theme-layout: key or fallback)
│   │   ├── main.html               # Base layout
│   │   ├── page.html               # Generic page
│   │   ├── post.html               # Blog post
│   │   └── ...
│   └── partials/roq-default/       # Theme partials
└── web-roq-default/                # Theme assets (CSS/JS)
```

**Layout resolution:**
- `layout: foo` → resolves local layout first (`templates/layouts/foo.html`), then theme layout as fallback
- `theme-layout: foo` → resolves directly (force) to theme layout (`templates/theme-layouts/<theme>/foo.html`)
- The old `:theme/` prefix syntax is deprecated (still works with a warning)

### Plugins (`roq-plugin/`)
Plugins extend Roq functionality:
- `asciidoc/` - AsciiDoc support (Java-based)
- `asciidoc-jruby/` - AsciiDoc support (full JRuby)
- `markdown/` - Markdown support
- `tagging/` - Tag system for collections
- `aliases/` - URL redirections
- `series/` - Post series support
- `sitemap/` - Sitemap generation
- `lunr/` - Client-side search
- `diagram/` - Diagram rendering (Kroki)
- `qrcode/` - QR code generation
- `faker/` - Fake data for testing

Each plugin follows the runtime/deployment pattern.

### Key Directories for Development

**Working on themes:**
- Templates: `roq-theme/default/runtime/src/main/resources/templates/`
- Assets: `roq-theme/default/runtime/src/main/resources/web-roq-default/`
- Rebuild: `cd roq-theme/default/runtime && mvn clean install -DskipTests`

**Working on blog content:**
- Content: `blog/content/`
- Custom layouts: `blog/templates/layouts/`
- Assets: `blog/web/` and `blog/public/`
- No rebuild needed (auto-detected by dev server)

**Working on plugins:**
- Plugin code: `roq-plugin/[plugin-name]/runtime/src/`
- Build-time: `roq-plugin/[plugin-name]/deployment/src/`
- Rebuild: `cd roq-plugin/[plugin-name]/runtime && mvn clean install -DskipTests`

## Testing and Verification

Dev server is already running on port 8080 and watches for changes automatically:
- **Changes in blog/ dir**: Auto-detected, no restart needed
- **Changes in roq modules** (roq-theme/, plugins/, etc.): Rebuild the specific module using mvncist, then tell me so I can restart the dev server

To rebuild a module after template or Java changes:
```bash
cd [module-dir] && mvn clean install -DskipTests
```
(CSS changes in blog/ are auto-detected)

To test rendering, use playwright plugin on the dev server.

### Checking Template Output

You can check the processed template output in:
```
blog/target/roq-templates/
```

**Directory Structure:**
- `target/roq-templates/full/` - Complete rendered HTML pages
- `target/roq-templates/content/` - Content-only rendered HTML (without full layout)
- `target/roq-templates/layouts/` - Resolved layout templates
- `target/roq-templates/theme-layouts/` - Theme layout templates

**Example files to check:**
- `target/roq-templates/full/docs/plugins.html` - AsciiDoc doc page with TOC
- `target/roq-templates/full/markups/asciidoc.html` - AsciiDoc markup test page
- `target/roq-templates/full/markups/markdown.html` - Markdown markup test page
- `target/roq-templates/full/index.html` - Home page with posts list

**Usage:**
1. Check the output files in `target/roq-templates/full/`
   2Verify template structure, layout includes, and content rendering
   3Look for `{#include roq-templates/layouts/[layout-name]}` at the top to see which layout is used

This is faster than starting dev mode for template verification during development.