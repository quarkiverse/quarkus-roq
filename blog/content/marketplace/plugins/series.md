---
title: Series
description: Organize posts into multi-part series with automatic navigation
layout: marketplace-plugin
icon: fa-solid fa-layer-group
install-name: series
tags: [collections, navigation]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/series
---

Join multiple posts into a series with automatic series headers.

Edit the layout for your posts, for example when using the roq-default theme:

{|
```html
<!-- templates/layouts/post-series.html -->
---
theme-layout: post
---

{#include partials/roq-series /}

{#insert /}
```
|}

Then use this layout and add the `series` attribute in the Front Matter of the posts you want to join:

```yaml
---
layout: post-series
title: Assemble your blog post in a series
description: Automatically series header for your posts
tags: plugin, frontmatter, guide, series
author: John Doe
series: My series Title
---
```

Use the exact same `series` title for all documents in the series.