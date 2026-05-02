---
title: Tagging
description: Auto-generate tag pages and filtered views for any content collection
layout: marketplace-plugin
icon: fa-solid fa-tags
install-name: tagging
tags: [collections, navigation]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/tagging
---

Generate a dynamic (derived) collection based on a given collection's tags. For example, if multiple posts have `tags: guide`, a `/posts/tag/guide` page is generated listing all matching posts. This works for any collection.

If you are using a theme that supports it (includes a tagging layout), you should now have tags pages available for all the tags in your posts!

> You can use [theme override](/docs/advanced/#overriding-theme) to customize the theme tagging layout.

To enable tagging without a theme, create a layout template and add `tagging: [collection id]` in FM. As a result you will have access to a new derived collection named `tagCollection`:

{|
```html
<!-- templates/layouts/tag.html -->
---
layout: main
tagging: posts
---

{#for post in site.collections.get(page.data.tagCollection)}
  <div>{post.title}</div>
{/for}
```
|}

This also supports pagination. Since tagging already specifies the target collection, pagination can be enabled with `paginate: true` in FM:

{|
```html
---
layout: main
tagging: posts
paginate: true
---

{#for post in site.collections.get(page.data.tagCollection).paginated(page.paginator)}
  <div>{post.title}</div>
{/for}
```
|}

### Template Extensions

| Usage | Description |
|---|---|
| `collection.allTags` | Returns a list of all tags from the collection, each tag slugified |
| `collection.tagsCount` | Returns a list of all tags slugified (name) with their count (count) in the collection |