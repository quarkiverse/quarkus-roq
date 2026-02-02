---
title: About
description: |
  Roq is a powerful static site generator that combines the best features of tools like Jekyll and Hugo, but within the Java ecosystem. It offers a modern approach with Quarkus at its core, requiring zero configuration to get started —ideal for developers who want to jump right in, while still being flexible enough for advanced users to hook into Java for deeper customization.
layout: :theme/page
---

# About Roq

Roq is a powerful static site generator that combines the best features of tools like Jekyll and Hugo, but within the Java ecosystem. It offers a modern approach with Quarkus at its core, requiring zero configuration to get started —ideal for developers who want to jump right in, while still being flexible enough for advanced users to hook into Java for deeper customization.

**This tool is a testament to how extensible and powerful Quarkus is, offering a low-risk yet highly capable platform that will evolve as demand grows.**

## Origins

I wrote a [blog post]({site.url('posts/roq-with-blogs')}) explaining how it all started.

## Credits

`Those are generated as a JSON by all-contributors, then we leverage roq-data to print them... slick 🏄!`

Thanks goes to these wonderful people:

<div data-qute> <!-- adding this div makes that we can edit this block nicely in the roq editor -->
  <div class="authors">
    {#for contributor in cdi:contributors.contributors}
      {#author-card name=contributor.name avatar=contributor.avatar_url nickname=contributor.login profile=contributor.profile }
        {#if cdi:authors.get(contributor.login)}
          <span class="author">author</span>
        {/if}
      {/author-card}
    {/for}
  </div>
</div>
