---
title: "Welcome to Roq!"
date: 2024-08-29 13:32:20 +0200
description: This is the first article ever made with Quarkus Roq
image: https://images.unsplash.com/photo-1505330622279-bf7d7fc918f4?q=80&w=3540&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
tags: blogging
author: ia3andy
redirect_from: [first-roq-article-ever]
---

Hello folks,

A bunch of Quarkus contributors started this new initiative to allow Static Site Generation with Quarkus (similar to Hugo, Jekyll, Lume, ...).

Quarkus already provides most of the pieces to create great web applications ([https://quarkus.io/guides/web][quarkus-web-docs]).

And Roq adds the missing pieces:

**Roq Generator:** allows to generate a static website out of any Quarkus application (it starts the app, fetch all the configured pages and assets, generate a static website and stop).

**Roq Data:** allows to create json or yaml data file and consume them from your templates. It is also possible to map them to beans to get type-safe validation in bonus!

**Roq FrontMatter:** allow to create pages and collections (posts, ...) using Markdown or Asciidoc with layouting. In fact, your static website content.

**What's missing?** we now need to incrementally add the toolkit to ease the process of creating static content through Quarkus:

- SEO
- Image processing ([quarkus-web-bundler/issues/42](https://github.com/quarkiverse/quarkus-web-bundler/issues/42))
- Pagination ([quarkus-roq/issues/65](https://github.com/quarkiverse/quarkus-web-bundler/issues/42))
- Advanced routing (redirect, ...)

**To go further:**

- Compat with tools like [https://frontmatter.codes/](https://frontmatter.codes/)
- Compat with IDEs plugins
- Roq GitHub action
- Dev-UI integrated headless CMS (to edit md/asciidoc on the fs)

With Roq you can develop the content using Quarkus dev-mode, and then generate (on CI) for Github Pages or similar when it's ready.

Bonus, everything added will benefit any "non-static" Quarkus app and any static Quarkus app could also go back to being non static.

This effort is now tracked using a "Focus Group" (temporary wording) project: https://github.com/orgs/quarkiverse/projects/6

This is a great opportunity to participate in a fun focus group and be involved with the Quarkus community, if anyone is interested in being a part of this, please reach out to me 🚀

There will be small, medium, bigger features to develop with any level of involvement. Participating could just be giving thoughts and discussing things..

Check out the [Roq docs][roq-docs] for more info on how to get the most out of Roq. File all bugs/feature requests at [Roq’s GitHub repo][roq-gh].

[quarkus-web-docs]: https://quarkus.io/guides/web
[roq-docs]: https://docs.quarkiverse.io/quarkus-roq/dev/index.html
[roq-gh]: https://github.com/quarkiverse/quarkus-roq
