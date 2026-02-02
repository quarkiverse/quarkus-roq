---
title: "Easily Generate a `sitemap.xml` for Your Site with Roq"
description: "Learn how to quickly set up and customize a sitemap.xml for your site using the Roq plugin."
tags: plugin,frontmatter,guide,cool-stuff
image: https://images.unsplash.com/photo-1488628176578-4ffd5fdbc900?q=80&w=4142&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
author: ia3andy
---

Creating a `sitemap.xml` for your site has never been easier! With the Sitemap plugin, you can automatically generate a well-structured sitemap for search engines to crawl your pages efficiently.

## Installation

To get started, install the plugin by running the following command:

```shell
quarkus ext add quarkus-roq-plugin-sitemap
```

## Setting Up the Sitemap

Next, create a new sitemap file in the `content/sitemap.xml`:


```xml
<!-- Include your sitemap template -->
\{#include fm/sitemap.xml}
```

And that's it! Your sitemap is now ready.

## Excluding Pages from the Sitemap

If there are pages you don't want included in the sitemap, simply set the `sitemap` property to `false` in the FM of those pages. For example:

```yaml
---
title: "Hidden Page"
sitemap: false
---
```

## Accessing Your Sitemap

Once your site is up and running, you can view your sitemap by navigating to:

```
http://localhost:8080/sitemap.xml
```

Congratulations! You’ve successfully set up a `sitemap.xml` for your site.
