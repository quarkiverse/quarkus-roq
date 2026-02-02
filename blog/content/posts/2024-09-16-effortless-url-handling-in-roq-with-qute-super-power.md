---
title: "Effortless URL Handling in Roq with Qute super-power"
date: 2024-09-16 13:32:20 +0200
description: Effortlessly manage both relative and absolute URLs with our enhanced Qute-powered feature. Utilizing the RoqUrl class, you can easily join and resolve paths, ensuring clean and predictable URLs. This update simplifies URL handling, making your code more efficient and your content easier to navigate and share.
image: https://images.unsplash.com/photo-1671530467085-40043a792439?q=80&w=3474&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
tags: cool-stuff, frontmatter, guide
author: ia3andy
---

Managing URLs is now very easy! With our updated Qute-powered feature, you can now manage relative and absolute URLs with more flexibility, thanks to new methods for joining paths and handling absolute URLs. Let’s explore some examples.

## How to Use It:

- **Relative URL Example (toString prints the relative url)**:

```html
<a class="post-thumbnail" href="\{post.url}"> </a>
```

- **Absolute URL Example**:

```html
<a class="post-thumbnail" href="\{post.url.absolute}"> </a>
```

- ** Smart URL:**

```html
<meta name="twitter:image:src" content="\{page.image.absolute}" >
```

There is a method in Page to retrieve the image url as a `RoqUrl` from the configured site images path. It is smart so that if the page image is external, it won't be affected.

## Under the Hood: The Power of RoqUrl

At the core of this feature is the RoqUrl class that you can leverage from Qute, which makes joining and resolving URLs super easy. With this structure, joining paths is as simple as calling resolve(). This ensures your URLs are clean, predictable, and easy to manage—whether they’re relative or absolute.

## Wrapping Up:

With Qute’s URL handling, you can now dynamically create and manage both relative and absolute URLs without any hassle. This new implementation will help keep your code clean while making it easier to navigate, link, and share content across your site.
