---
title: Aliases
description: Set up URL redirects and short links to keep old URLs working
layout: marketplace-plugin
icon: fa-solid fa-shuffle
install-name: aliases
tags: [navigation, seo]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/aliases
---

Create one or many aliases (redirections) for a page. Add `aliases: [your-alias-here, another-alias-here]` in the Front Matter to access the page using a customized URL.

```yaml
# content/posts/2024-08-29-welcome-to-roq.md
---
layout: post
title: "Welcome to Roq!"
date: 2024-08-29 13:32:20 +0200
description: This is the first article ever made with Quarkus Roq
tags: blogging
aliases: [first-roq-article-ever]
---
```

Now, when you access `http://localhost:8080/first-roq-article-ever`, you will be redirected to the `2024-08-29-welcome-to-roq` blog post.

> You can use link templating in aliases.