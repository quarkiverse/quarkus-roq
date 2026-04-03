---
title: "The First Roq!"
description: This is my first article ever made with Quarkus Roq
image: blog.avif
tags: blogging
author: roqqy
cool: this is cool :)
fun: and fun!
---

You can access page data like this:
```markdown
* \{page.data.cool}
* \{page.data.fun}
```
**will render ⤵**

* {page.data.cool}
* {page.data.fun}


There are a few helpers on the `page` variable ([more on variables](https://iamroq.com/docs/basics/#_variables)):

```markdown
> \{page.date.format('YYYY')}: \{page.description}
```
**will render ⤵**

> {page.date.format('YYYY')}: {page.description}

---

It's time to write awesome articles!

__Thank you!__

**PS:** To make the tag work ([#blogging]({site.url.resolve('posts/tag/blogging')})), you need to [enable tagging](https://iamroq.com/docs/plugins/#plugin-tagging).