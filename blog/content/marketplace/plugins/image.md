---
title: Image
description: Responsive image processing with automatic resizing, format conversion, and srcset generation
layout: marketplace-plugin
icon: fa-solid fa-image
install-name: image
tags: [content, media, performance]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/image
search-boost: 1.2
---

Process images at build time to generate responsive `<picture>` elements with multiple sizes and formats. Images are resized, converted, and served with proper `srcset` attributes for optimal loading.

## Installation

```shell
roq add plugin:image
```

## Built-in presets

The plugin ships with five ready-to-use presets:

| Preset | Use case | Widths | Crop |
|--------|----------|--------|------|
| `roq-hero` | Hero / banner images | 400, 800, 1200 | — |
| `roq-post-card` | Post listing thumbnails | 160, 320, 640 | 3:2 center |
| `roq-avatar` | Profile / author photos | 48, 72, 96 | 1:1 center (pixel-ratio) |
| `roq-content` | General in-content images | 400, 800 | — |
| `roq-og` | Social card (`og:image`), used by {|`{#seo /}`|} | 1200 | 1.91:1 center, `jpg` only |

A width larger than the image is replaced by the image's width, so a preset always yields a variant and a
small image is never upscaled. The generated urls carry `quarkus.http.root-path`, the one the Roq GitHub action
sets for project sites. All but `roq-og` produce `webp` + `jpg` at quality 80–85. Without libvips, `webp` is skipped and only `jpg` variants are generated (no error). For `webp`/`avif` support, add the `quarkus-qute-web-image-vips` extension.

Override or extend them in your `application.properties`.

## Custom presets

Define your own named presets in your configuration:

```properties
quarkus.qute.image.presets.hero.formats=webp,jpg
quarkus.qute.image.presets.hero.fallback-format=jpg
quarkus.qute.image.presets.hero.widths=350,600,1000
quarkus.qute.image.presets.hero.quality=80
```

### Cropping

```properties
quarkus.qute.image.presets.avatar.widths=48,72,96
quarkus.qute.image.presets.avatar.crop.ratio=1:1
quarkus.qute.image.presets.avatar.crop.keep=center
```

### Pixel ratio (for fixed-size elements)

```properties
quarkus.qute.image.presets.avatar.widths=48,72,96
quarkus.qute.image.presets.avatar.pixel-ratio.base-width=48
```

### Direct URL output

When you need just the URL (e.g. for lightbox links), use `direct-url`:

```properties
quarkus.qute.image.presets.lightbox.widths=800
quarkus.qute.image.presets.lightbox.direct-url=true
```

This outputs a plain URL string instead of a `<picture>` element.

## Usage in templates

Use the `\{#img}` tag with a `src` and `preset`:

{|
```html
{#img src=page.image preset='hero' alt='Hero image'/}
```
|}

### Image sources

**FrontMatter image** (declared in the page header, next to the page or in `public/images/` for pages
without their own directory):

{|
```yaml
---
image: hero.jpg
---
```
```html
{#img src=page.image preset='roq-hero' alt='My hero'/}
```
|}

**Relative path** (a file next to the page):

{|
```html
{#img 'photo.jpg' preset='roq-content' alt='A photo'/}
```
|}

**Absolute path** (from `public/` or `static/`):

{|
```html
{#img '/images/logo.jpg' preset='roq-post-card' alt='Logo'/}
```
|}

**Remote URL** (downloaded at build time, as a literal or from the front matter):

{|
```yaml
---
image: https://images.unsplash.com/photo-1457365050282-c53d772ef8b2?w=1200
---
```
|}

Only `https://` URLs are downloaded (`quarkus.qute.image.remote.allow-http=true` for plain http), cached under the
build directory so a URL is fetched once. A download that fails or is not an image under 20 MB and 50 megapixels
fails the build like a missing file. `quarkus.qute.image.remote.enabled=false` renders remote URLs as they are, and
committing the build directory (`quarkus.qute.image.build-dir=../.qute-image`) pins the images you trust.

### Expressions are resolved at build time

All images are converted at build time, including the ones a template gets from an expression. The plugin
evaluates `page.image`, `page.image('other.jpg')`, `site.image('logo.jpg')` and the expressions of a tag
or layout against every page that renders it, for instance `post.image` in a post card used by a listing:

{|
```html
{#for post in site.collections.posts}
  {#img src=post.image preset='roq-post-card' alt=post.title/}
{/for}
```
|}

A page without that image renders nothing. An expression the build cannot evaluate, or an image that does
not exist, fails the build (the plugin enables `quarkus.qute.image.strict`), so a broken image is never
shipped.

### Paths and URLs

For a CSS background or a link, `img:path` returns the generated path and `img:url` a URL object with
`.absolute`:

{|
```html
<a class="thumbnail" style='background-image: url("{img:path(post.image, "roq-post-card")}")'></a>
<meta property="og:image" content="{img:url(page.image, 'roq-og').absolute}" />
```
|}

The second line is what {|`{#seo /}`|} emits for the social card. The `roq-og` preset only produces a raster
variant, and an SVG page image is sent as it is, so use a raster image for pages that need a social card.

### Other expressions

An expression that is neither a page, site nor document expression, a loop over front matter data for
instance, cannot be evaluated at build time. The build warns and leaves it to the render-time lookup: cover
those images with a `quarkus.qute.image.generate` glob so their variants exist.

### Without the plugin

Themes can use `\{#img}` whether or not the plugin is installed: without it, Roq renders a plain `<img>` with
the source and the `alt`, `class`, `loading`, `width` and `height` parameters, and the `img:` namespace returns
the original paths.

### Originals

By default the original files of the converted images are not published: they are neither served nor
written to the generated site, only their variants are. Pages still resolve them, so the front matter and the
tags above keep working. Sources used as they are (SVG, or a format the converter cannot read) stay published.

Anything that points at an original outside the image tags, such as {|`{page.image}`|} in plain HTML or
a Markdown image, is a broken link in that case. To keep the originals published:

```properties
quarkus.roq.plugin.image.publish-originals=true
```

## WebP / AVIF support with libvips

By default, the plugin uses Java's ImageIO for resizing, which supports `jpg` and `png` but not `webp` or `avif`. To enable modern formats, add the vips extension:

```shell
roq add io.quarkiverse.qute.web:quarkus-qute-web-image-vips
```

This requires [libvips](https://www.libvips.org/install.html) installed on your system:

```shell
# macOS
brew install vips

# Debian / Ubuntu
sudo apt install libvips-dev

# Fedora
sudo dnf install vips-devel
```

The vips converter uses Java's FFM (Foreign Function & Memory) API to call libvips natively. It is significantly faster than ImageIO and supports `webp`, `avif`, `heif`, and other formats.

> Without libvips, presets that list `webp` or `avif` in their formats still work. Those formats are skipped with a build warning, and the fallback format (`jpg`) is always generated.
