---
layout: post
title: Mastering Pagination in Roq
img: https://images.unsplash.com/photo-1502126829571-83575bb53030?q=80&w=3474&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
description: Learn how to implement pagination in Roq to enhance your content navigation. This article walks through the process of adding pagination, configuring page size, and customizing links.
author: ia3andy
date: 2024-09-20 14:00:00 +0200
---

Adding pagination to your Roq site is an easy way to improve content navigation. Let’s walk through how to implement pagination and customize its behavior in your site.

## Step 1: Basic Pagination Setup

First, include the following in your frontmatter on the page which will iterate on the paginated collection:

```yaml
layout: main
paginate: posts
```

Next, in your template, loop through the paginated posts using:

```html
\{#for post in site.collections.posts.paginated(page.paginator)}
<article class="post">
  ...
</article>
\{/for}
```

## Step 2: Adding Pagination Controls

To add pagination controls, add something like this to `_includes/pagination.html` and include it in your page `\{#include pagination.html/}`:

```html
<div class="container">
  <nav class="pagination" role="pagination">
    <ul>
      \{#if page.paginator.previous}
      \{#if page.paginator.isSecond}
      <p><a class="newer-posts" href="\{site.url}"><i class="fa fa-long-arrow-left" aria-hidden="true"></i></a></p>
      \{#else}
      <p><a class="newer-posts" href="\{page.paginator.previous)}/"><i class="fa fa-long-arrow-left" aria-hidden="true"></i></a></p>
      \{/if}
      \{/if}

      \{#if page.paginator.total > 1}
      <p><span class="page-number">Page \{page.paginator.currentIndex} of \{page.paginator.total}</span></p>
      \{/if}

      \{#if page.paginator.next}
      <p><a class="older-posts" href="\{page.paginator.next)}"><i class="fa fa-long-arrow-right" aria-hidden="true"></i></a></p>
      \{/if}
    </ul>
  </nav>
</div>
```

You can further customize your pagination by setting the page size and link format:
```yaml
paginate: 
  size: 4
  collection: posts
  link: posts/page-:page
```

With these steps, you can create a flexible pagination system to improve your site’s navigation.
