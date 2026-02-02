---
title: RSS Feed of your blog posts
description: Automatically generate an RSS feed of your blog links.
image: https://images.unsplash.com/photo-1520636902994-f596446c3728?q=80&w=3198&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
tags: plugin,frontmatter,guide
author: melloware
aliases: [rss]
---

Adding RSS is as easy as adding this tag to your `<head>` section:

```html
\{#rss site /}
```

Like this:

```
{#rss site /}

```

It will automatically utilize the Frontmatter data from all your blog posts to generate a valid Atom RSS feed link at `rss.xml`. Ensure you create an `rss.xml` file at the root of your site and include this single line of code:

```html
\{#include fm/rss.html /}
```

The Atom Syndication Format is an XML language used for web feeds. A web feed (also called ‘news feed’ or ‘RSS feed’) is a data format used for providing users with frequently updated content. Content distributors syndicate a web feed, thereby allowing users to subscribe a channel to it. A typical scenario of web-feed use might involve the following: a content provider publishes a feed link on its site which end users can register with an aggregator program (also called a feed reader or a newsreader) running on their own machines.

```xml
{#include fm/rss.html}
```
