---
title: The second Roq plugin is for redirecting your page to a better place!
image: https://images.unsplash.com/photo-1691210781188-b8c69945e620?q=80&w=1899&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
description: We introduced a way to declare aliases in FrontMatter. It is now easy create redirections to your blog posts!
author: mcruzdev
tags: plugin, frontmatter, guide, cool-stuff
aliases: [aliases-very-cool, aliases-4-ever, aliases-again]
series: roq-plugins
---

In the last post, we saw how easy it is to use Quarkus for static site generator (@ia3andy's was right!). I am excited to share that we now have a new plugin that allows you to set up redirects for your blog posts! For this post, I've created three aliases.

- [aliases-very-cool](<{site.url('/aliases-very-cool/')}>)
- [aliases-4-ever](<{site.url('/aliases-4-ever/')}>)
- [aliases-again](<{site.url('/aliases-again/')}>)

> If you click on at least one alias, you will be redirected here again!

**And how did I work my magic to set this up?**

Step 1: Add the aliases plugin in your dependencies file:

```xml
 <dependency>
    <groupId>io.quarkiverse.roq</groupId>
    <artifactId>quarkus-roq-plugin-aliases</artifactId>
    <version>...</version>
</dependency>
```

Step 2: Add a new entry `aliases: [name-of-aliases-here]` in your FM data.

In this blog post I used the following FM:

```markdown
...
aliases: [aliases-very-cool, aliases-4-ever, aliases-again]
...
```

For more info [check out the doc](https://iamroq.com/docs/plugins/#plugin-aliases).
