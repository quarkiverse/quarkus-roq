---
layout: post
title: "Effortless URL Handling in Roq with Qute super-power"
date: 2024-09-16 13:32:20 +0200
description: Effortlessly manage both relative and absolute URLs with our enhanced Qute-powered feature. Utilizing the RoqUrl class, you can easily join and resolve paths, ensuring clean and predictable URLs. This update simplifies URL handling, making your code more efficient and your content easier to navigate and share.
img: https://images.unsplash.com/photo-1671530467085-40043a792439?q=80&w=3474&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
author: ia3andy
---

Managing URLs is now very easy! With our updated Qute-powered feature, you can now manage relative and absolute URLs with more flexibility, thanks to new methods for joining paths and handling absolute URLs. Let’s explore some examples.

## How to Use It:

- **Relative URL Example**:
```html
<a 
    class="post-thumbnail" 
    style="background-image: url(\{site.rootUrl.relative('static/images/').resolve(post.img)})" 
    href="\{post.url.relative}">
</a>
```


- **Absolute URL Example (great for social media previews):**
```html
<meta name="twitter:image:src" content="\{site.rootUrl.absolute.resolve('/static/assets/img/').resolve(page.img)}">
```

## Under the Hood: The Power of RoqUrl

At the core of this feature is the RoqUrl class that you can leverage from Qute, which makes joining and resolving URLs super easy.

## RoqUrl implementation:

Here’s how the RoqUrl class is coded behind the scenes:

```java

public record RoqUrl(String path) {

    /**
     * Create a new Url joining the other path
     *
     * @param other the other path to join
     * @return the new joined url
     */
    public RoqUrl resolve(Object other) {
        return new RoqUrl(PathUtils.join(path, other.toString()));
    }

    /**
     * {@See RoqUrl#resolve}
     *
     * @param other the other path to join
     * @return the new joined url
     */
    public RoqUrl join(Object other) {
        return this.resolve(other);
    }

    /**
     * Create a new Url from the given path/url
     *
     * @param from the url to join from
     * @return the new joined url
     */
    public RoqUrl from(Object from) {
        return new RoqUrl(PathUtils.join(from.toString(), path));
    }

    /**
     * Check if this is a absolute Url starting with http:// or https://
     *
     * @return true is it's an absolute url
     */
    public boolean isAbsolute() {
        return path.startsWith("http://") || path.startsWith("https://");
    }

    /**
     * Return itself if it absolute or from the given url if it's not.
     * This is useful for blog images which can be absolute or relative to the blog image directory.
     *
     * @param other the url to join from
     * @return either the url if it's absolute or the joined url if it's not
     */
    public RoqUrl absoluteOrElseFrom(String other) {
        return isAbsolute() ? this : from(other);
    }


    @Override
    public String toString() {
        return path;
    }
}

```

With this structure, joining paths is as simple as calling resolve() or from(). This ensures your URLs are clean, predictable, and easy to manage—whether they’re relative or absolute.

We also provide a Qute extension to convert a String to a RoqUrl:
```html
\{page.img.toUrl.absoluteOrElseFrom(site.rootUrl.relative.resolve('static/images/'))}
```


## Wrapping Up:

With Qute’s URL handling, you can now dynamically create and manage both relative and absolute URLs without any hassle. This new implementation will help keep your code clean while making it easier to navigate, link, and share content across your site.