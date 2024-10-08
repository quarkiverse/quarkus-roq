---
layout: post
title: "Effortless URL Handling in Roq with Qute super-power"
date: 2024-09-16 13:32:20 +0200
description: Effortlessly manage both relative and absolute URLs with our enhanced Qute-powered feature. Utilizing the RoqUrl class, you can easily join and resolve paths, ensuring clean and predictable URLs. This update simplifies URL handling, making your code more efficient and your content easier to navigate and share.
img: https://images.unsplash.com/photo-1671530467085-40043a792439?q=80&w=3474&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
tags: cool-stuff, frontmatter, guide
author: ia3andy
---

Managing URLs is now very easy! With our updated Qute-powered feature, you can now manage relative and absolute URLs with more flexibility, thanks to new methods for joining paths and handling absolute URLs. Let’s explore some examples.

## How to Use It:

- **Relative URL Example (toString prints the relative url)**:
```html
<a 
    class="post-thumbnail" 
    href="\{post.url}">
</a>
```

- **Absolute URL Example**:
```html
<a 
    class="post-thumbnail" 
    href="\{post.url.absolute}">
</a>
```

- ** Smart URL:**
```html
<meta name="twitter:image:src" content="\{page.img.absolute}">
```

There is a method in Page to retrieve the image url as a `RoqUrl` from the configured site images path. It is smart so that if the page image is external, it won't be affected.

## Under the Hood: The Power of RoqUrl

At the core of this feature is the RoqUrl class that you can leverage from Qute, which makes joining and resolving URLs super easy.

## RoqUrl implementation:

Here’s how the RoqUrl class is coded behind the scenes:

```java

public record RoqUrl(RootUrl rootUrl, String path) {

    public RoqUrl(RootUrl rootUrl, String path) {
        this.path = path;
        this.rootUrl = rootUrl;
    }

    @Override
    public String toString() {
        return relative();
    }

    public String relative() {
        if (isExternal()) {
            return path();
        }
        return PathUtils.join(rootUrl.rootPath(), path());
    }

    public String absolute() {
        if (isExternal()) {
            return path();
        }
        return PathUtils.join(rootUrl().absolute(), path());
    }

    public String encoded() {
        return URLEncoder.encode(absolute(), StandardCharsets.UTF_8);
    }

    /**
     * Check if this is an absolute Url starting with http:// or https://
     *
     * @return true is it's an absolute url
     */
    public static boolean isPathAbsolute(String path) {
        return path.startsWith("http://") || path.startsWith("https://");
    }

    /**
     * Create a new Url joining the other path
     *
     * @param other the other path to join
     * @return the new joined url
     */
    public RoqUrl resolve(Object other) {
        if (isPathAbsolute(other.toString())) {
            return new RoqUrl(null, other.toString());
        }
        return new RoqUrl(rootUrl(), PathUtils.join(path(), removeTrailingSlash(other.toString())));
    }

    /**
     * {@see Resolvable#resolve}
     *
     * @param other the other path to join
     * @return the new joined url
     */
    public RoqUrl join(Object other) {
        return this.resolve(other);
    }

    /**
     * This will just append the given string (without adding the `/`)
     *
     * @param other the string to append
     * @return the concatenated url
     */
    public RoqUrl append(Object other) {
        return new RoqUrl(rootUrl(), path() + other.toString());
    }

    public boolean isExternal() {
        return rootUrl() == null;
    }

    public static RoqUrl fromRoot(RootUrl rootUrl, String path) {
        if (isPathAbsolute(path)) {
            return new RoqUrl(null, path);
        }
        return new RoqUrl(rootUrl, removeTrailingSlash(path));
    }

}




```

With this structure, joining paths is as simple as calling resolve(). This ensures your URLs are clean, predictable, and easy to manage—whether they’re relative or absolute.


## Wrapping Up:

With Qute’s URL handling, you can now dynamically create and manage both relative and absolute URLs without any hassle. This new implementation will help keep your code clean while making it easier to navigate, link, and share content across your site.