---
title: About
description: |
  Roq stands out in the Java development community as a powerful static site generator, bridging the gap between the
  likes of Gatsby, Hugo, and the broader backend community. With GitHub Actions support out-of-the-box, Roq is easy to use for beginners, but also flexible enough to provide
  Java hooks for advanced users.
layout: :theme/page
---

# About Roq

Roq stands out in the Java development community as a powerful static site generator, bridging the gap between the
likes of Gatsby, Hugo, and the broader backend community. With GitHub Actions support out-of-the-box, Roq is easy to use for beginners, but also flexible enough to provide
Java hooks for advanced users.

## Authors

<div class="authors">
  <!-- authors.yml is in the data/ -->
  {#for id in cdi:authors.fields}
    {#let author=cdi:authors.get(id)}
    <!-- the author-card tag is defined in the default Roq theme -->
    {#author-card name=author.name avatar=author.avatar nickname=author.nickname profile=author.profile /}
  {/for}
</div>

