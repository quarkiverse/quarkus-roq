---
title: Linktree Theme
description: Build a personal link-tree to share your links, social profiles, and QR codes
layout: marketplace-theme
icon: fa-solid fa-link
install-name: linktree
tags: [linktree, links, responsive, tailwind, qrcode]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-theme/linktree
search-boost: 20
---

Build a personal link-tree site with data-driven YAML configuration, social icons, and auto-generated QR codes.

### Data Files

Add your profile and links in the `data/` directory:

**profile.yml**

```yaml
name: Ada Lovelace
handle: "@adalovelace"
title: Computational Pioneer
image: ada.png
bio: |
  Ada Lovelace was a 19th-century mathematician known for her
  visionary work on Charles Babbage's Analytical Engine.
tree: my-links
social:
  - name: GitHub
    url: https://github.com/adalovelace
    icon: github-logo
  - name: LinkedIn
    url: https://www.linkedin.com/in/adalovelace
    icon: linkedin-logo
```

**trees/my-links.yml**

```yaml
title: Ada's Links
description: Resources and writings by Ada Lovelace
links:
  - name: The Analytical Engine
    url: https://en.wikipedia.org/wiki/Analytical_engine
    description: The machine that started it all
    icon: gear
  - name: Quarkus
    url: https://quarkus.io
    description: Supersonic Subatomic Java framework
    icon: lightning
```

### Features

- **Data-driven**: profile and links defined in YAML, mapped to typed Java records via `@DataMapping`
- **Multiple trees**: drop a new YAML file in `data/trees/` and a page is auto-generated
- **Social icons**: Phosphor Icons for social media links (GitHub, LinkedIn, Bluesky, and more)
- **QR codes**: built-in QR code generation for each tree page with download support
- **Dark mode**: automatic light/dark theme based on system preference
- **Tailwind CSS**: fully styled with Tailwind utility classes

### Icons

Icons use [Phosphor Icons](https://phosphoricons.com/). Browse the catalog and use the icon name in your YAML:

```yaml
icon: github-logo    # renders as <i class="ph ph-github-logo">
icon: lightning       # renders as <i class="ph ph-lightning">
```

### Adding More Trees

Create a new YAML file in `data/trees/`:

```yaml
# data/trees/work-links.yml
title: Work Links
description: Professional resources
links:
  - name: Portfolio
    url: https://example.com
    description: My work
    icon: briefcase
```

A new page at `/work-links/` is generated automatically, with its own QR code on the `/trees` gallery page.
