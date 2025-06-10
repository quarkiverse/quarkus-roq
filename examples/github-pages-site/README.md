# ROQ GitHub Pages Example

This example demonstrates how to use ROQ's new base path configuration feature to deploy a static site to GitHub Pages.

## Features Demonstrated

- ✅ **Base Path Configuration** - Environment-specific base paths for dev/prod
- ✅ **Asset Path Helper** - Automatic base path resolution for CSS, JS, images
- ✅ **Enhanced Frontmatter** - Custom frontmatter property access in templates
- ✅ **GitHub Pages Deployment** - Complete CI/CD workflow
- ✅ **Responsive Navigation** - Base path aware navigation and links

## Quick Start

### 1. Clone and Configure

```bash
git clone <your-repo>
cd github-pages-site
```

### 2. Update Configuration

Edit `src/main/resources/application.properties`:

```properties
# Replace 'my-awesome-docs' with your repository name
%prod.site.base-path=/your-repository-name
```

### 3. Local Development

```bash
# Development mode (no base path)
mvn quarkus:dev
```

Visit http://localhost:8080 - notice URLs work without base path.

### 4. Production Build

```bash
# Production build (with base path)
mvn clean compile quarkus:build -Dquarkus.profile=prod
java -jar target/quarkus-app/quarkus-run.jar --roq.generator
```

Check `target/roq/` - all URLs include the base path.

### 5. Deploy to GitHub Pages

1. Push to your repository
2. Enable GitHub Pages in repository settings
3. The GitHub Actions workflow will automatically deploy

## Key Configuration

### Base Path Setup

```properties
# application.properties

# Development: no base path (served from root)
%dev.site.base-path=

# Production: GitHub Pages serves from /repository-name
%prod.site.base-path=/my-awesome-docs
```

### Template Usage

#### Asset Loading
```html
<!-- Automatically includes base path -->
<link rel="stylesheet" href="{site.assetPath('/css/style.css')}">
<script src="{site.assetPath('/js/app.js')}"></script>
```

#### Navigation Links
```html
<nav>
    <a href="{site.basePath}/">Home</a>
    <a href="{site.basePath}/docs/">Docs</a>
    <a href="{site.basePath}/blog/">Blog</a>
</nav>
```

#### Custom Frontmatter
```html
{#if page.data.show-sidebar}
<aside class="sidebar">...</aside>
{/if}

{#if page.data.difficulty == 'beginner'}
<div class="beginner-notice">...</div>
{/if}
```

## File Structure

```
src/main/resources/site/
├── content/
│   ├── index.html              # Homepage with custom frontmatter
│   └── pages/
│       └── docs/
│           └── getting-started.html  # Documentation page
├── templates/
│   └── main.html               # Main layout template
└── public/
    ├── css/
    ├── js/
    └── images/
```

## Environment Differences

### Development (`mvn quarkus:dev`)
- Base path: `` (empty)
- URLs: `/css/style.css`, `/docs/`
- Perfect for local development

### Production (`-Dquarkus.profile=prod`)
- Base path: `/my-awesome-docs`
- URLs: `/my-awesome-docs/css/style.css`, `/my-awesome-docs/docs/`
- Ready for GitHub Pages

## Advanced Features

### Custom Frontmatter Properties

Pages can define custom properties:

```yaml
---
title: My Page
custom-layout: special
show-sidebar: true
difficulty: beginner
tags: ["java", "quarkus"]
---
```

Access in templates:

```html
{#if page.data.custom-layout == 'special'}
<div class="special-layout">...</div>
{/if}

<div class="difficulty {page.data.difficulty}">
    Level: {page.data.difficulty}
</div>
```

### Conditional Content

```html
{#if page.data.show-breadcrumbs}
<nav class="breadcrumbs">...</nav>
{/if}

{#if page.data.enable-search}
<script src="{site.assetPath('/js/search.js')}"></script>
{/if}
```

## Deployment

The included GitHub Actions workflow:

1. **Builds** the project with production profile
2. **Generates** static site with correct base paths
3. **Deploys** to GitHub Pages automatically

All URLs in the generated site will include the base path, making them work correctly on GitHub Pages.

## Troubleshooting

### URLs not working locally
- Ensure `%dev.site.base-path=` (empty) in configuration

### URLs not working on GitHub Pages
- Check `%prod.site.base-path=/repository-name` matches your repo name
- Verify GitHub Pages is enabled and pointing to the right branch

### Assets not loading
- Use `{site.assetPath('/path')}` instead of hardcoded paths
- Check that asset files exist in `src/main/resources/site/public/`

## Migration from Hardcoded Paths

**Before:**
```html
<link rel="stylesheet" href="/my-project/css/style.css">
<a href="/my-project/docs/">Documentation</a>
```

**After:**
```html
<link rel="stylesheet" href="{site.assetPath('/css/style.css')}">
<a href="{site.basePath}/docs/">Documentation</a>
```

This approach:
- ✅ Works in all environments
- ✅ No manual path switching
- ✅ Clean, maintainable templates
- ✅ Production-ready deployment
