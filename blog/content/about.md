---
title: About
description: |
  Roq stands out in the Java development community as a powerful static site generator, bridging the gap between the
  likes of Gatsby, Hugo, and the broader backend community. With GitHub Actions support out-of-the-box, Roq is easy to use for beginners, but also flexible enough to provide
  Java hooks for advanced users.
layout: :theme/page
---

# About Roq

Roq stands out in the Java development community as a powerful static site generator, bridging the gap between the likes of Gatsby, Hugo, and the broader backend community. As Andy Damevin explains, while tools like Jekyll once filled this space, they’ve become outdated and cumbersome, especially with Ruby’s limitations. Roq offers a modern solution, leveraging Quarkus with no configuration needed, allowing developers to start quickly using Codestart and simply editing their site directory.

With GitHub Actions support out-of-the-box, Roq is easy to use for beginners, but also flexible enough to provide Java hooks for advanced users.

**This tool is a testament to how extensible and powerful Quarkus is, offering a low-risk yet highly capable platform that will evolve as demand grows.**

## Credits

`Those are generated as a JSON by all-contributors, then we leverage roq-data to print them... slick 🏄!`

Thanks goes to these wonderful people:

<div class="authors">
  {#for contributor in cdi:contributors.contributors}
  {#author-card name=contributor.name avatar=contributor.avatar_url nickname=contributor.login profile=contributor.profile }
  {#if cdi:authors.get(contributor.login)}
  <span class="author">author</span>
  {/if}
  {/author-card}
  {/for}
</div>
