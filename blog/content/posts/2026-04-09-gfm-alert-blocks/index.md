---
title: "GFM Alert Blocks: Styled Callouts in Your Markdown"
image: https://images.unsplash.com/photo-1516321318423-f06f85e504b3?w=800
tags: markdown, features, gfm
description: Roq supports GitHub Flavored Markdown alert blocks with icons and themed colors. Learn how to use NOTE, TIP, IMPORTANT, WARNING, and CAUTION blocks, and how to add custom alert types.
date: 2026-05-04 11:00:00 +0200
series: roq-2.1
---

Roq now supports **GitHub Flavored Markdown (GFM) alert blocks** (also known as admonition blocks) — the styled callouts you see on GitHub READMEs and issues, complete with icons and color themes:

> [!NOTE]
> Useful information that users should know, even when skimming content.

> [!TIP]
> Helpful advice for doing things better or more easily.

> [!IMPORTANT]
> Key information users need to achieve their goal.

> [!WARNING]
> Urgent info that needs immediate user attention to avoid problems.

> [!CAUTION]
> Advises about risks or negative outcomes of certain actions.

## How It Works

Alert blocks use a special blockquote syntax with a type identifier:

```markdown
> [!NOTE]
> Your note content here.
```

The five standard types are:

| Type | Icon | Color | Use Case |
|------|------|-------|----------|
| `NOTE` | Info circle | Blue | General information |
| `TIP` | Light bulb | Green | Helpful suggestions |
| `IMPORTANT` | Verified badge | Purple | Critical information |
| `WARNING` | Alert triangle | Orange | Potential issues |
| `CAUTION` | Stop octagon | Red | Dangerous actions |

Icons are from [GitHub Octicons](https://github.com/primer/octicons) (MIT license).

## Custom Alert Types

You can configure custom alert types beyond the standard five. Add this to your `application.properties`:

```properties
quarkus.qute.web.markdown.plugin.alerts.custom-types.INFO=Information
quarkus.qute.web.markdown.plugin.alerts.custom-types.BUG=Known Bug
quarkus.qute.web.markdown.plugin.alerts.custom-types.SECURITY=Security Notice
```

Then use them in your markdown:

```markdown
> [!INFO]
> This is a custom info alert.

> [!BUG]
> This is a known issue.

> [!SECURITY]
> This is a security notice.
```

Custom alert types get basic styling (border, padding, rounded corners) but **no icon or color** by default. To add them, see the Styling section below.

Custom alerts without custom CSS:

> [!INFO]
> This alert has basic styling but no icon or color.

> [!BUG]
> Same here — add custom CSS to style it.

> [!SECURITY]
> And this one too.

## Styling

The `roq-default` theme includes full styling for the 5 standard GFM alert types: icons, colored borders, pastel backgrounds, and dark mode support.

### How SVG Icons Work

Icons use the `mask-image` + `background-color` technique. The SVG defines only the **shape** (mask), and `background-color: var(--alert-color)` fills that shape with a color. This means one SVG works in any color — including dark mode.

The SVGs are inlined as **data URIs** in the CSS using URL-encoded format:

```css
--alert-icon: url("data:image/svg+xml,%3Csvg%20xmlns%3D...%3E%3Cpath%20d%3D%22...%22/%3E%3C/svg%3E");
```

Icons are from [GitHub Octicons](https://github.com/primer/octicons) (MIT license).

### For Custom Themes

If you're using a custom theme, add alert styling to your CSS:

```css
.markdown-alert {
  padding: 1rem;
  margin: 1rem 0;
  border-radius: 0.5rem;
  border-left: 4px solid;
}

.markdown-alert-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.markdown-alert-title::before {
  content: "";
  display: inline-block;
  width: 1rem;
  height: 1rem;
  flex-shrink: 0;
  background-color: var(--alert-color);
  mask-image: var(--alert-icon, none);
  mask-size: 100%;
  mask-repeat: no-repeat;
  mask-position: center;
}

/* Standard types */
.markdown-alert-note {
  --alert-color: #0969da;
  --alert-icon: url("data:image/svg+xml,..."); /* info-16 SVG as data URI */
  border-color: #0969da;
  background: #0969da08;
}
.markdown-alert-note .markdown-alert-title { color: #0969da; }
```

### Adding Icons & Colors for Custom Types

To style a custom alert type (e.g., `INFO`), add CSS with the `--alert-icon` and `--alert-color` variables:

```css
.markdown-alert-info {
  --alert-color: #0550ae;
  --alert-icon: url("data:image/svg+xml,..."); /* your SVG as data URI */
  border-color: #0550ae;
  background: #0550ae08;
}
.markdown-alert-info .markdown-alert-title { color: #0550ae; }

.markdown-alert-bug {
--alert-color: #cf222e;
--alert-icon: url("data:image/svg+xml,..."); /* bug SVG as data URI */
border-color: #cf222e;
background: #cf222e08;
}
.markdown-alert-bug .markdown-alert-title { color: #cf222e; }

.markdown-alert-security {
  --alert-color: #da3633;
  --alert-icon: url("data:image/svg+xml,..."); /* shield-16 SVG */
  border-color: #da3633;
  background: #da363308;
}
.markdown-alert-security .markdown-alert-title { color: #da3633; }
```

Which of these alert blocks will you use first?
