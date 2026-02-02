---
layout: :theme/post
title: The first Roq plugin is for tagging (with pagination)
image: tagging.png
description: We introduced the first Roq plugin, it is for collection tagging & with pagination support!
author: ia3andy
tags: plugin, frontmatter, guide, cool-stuff
series: roq-plugins
---

My mind is getting blown by how much Quarkus was made for Static Site Generation. I just implemented a new plugin to generate tag pages and that was soooo easy.

To use it:

```xml
 <dependency>
    <groupId>io.quarkiverse.roq</groupId>
    <artifactId>quarkus-roq-plugin-tagging</artifactId>
    <version>...</version>
</dependency>
```

and adding a new `layouts/tag.html` page or any layout with `tagging: [name of collection]` as FM data.

For more info [check out the doc](https://iamroq.com/docs/plugins/#plugin-tagging).
