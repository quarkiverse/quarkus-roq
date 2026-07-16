---
title: Tagging
description: Auto-generate tag pages and filtered views for any content collection
layout: marketplace-plugin
icon: fa-solid fa-tags
install-name: tagging
tags: [collections, navigation]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/tagging
search-boost: 1.2
---

Generate a dynamic (derived) collection based on a given collection's tags. For example, if multiple posts have `tags: guide`, a `/posts/tag/guide` page is generated listing all matching posts. This works for any collection.

If you are using a theme that supports it (includes a tagging layout), you should now have tags pages available for all the tags in your posts!

> You can use [theme override](/docs/advanced/#overriding-theme) to customize the theme tagging layout.

To enable tagging without a theme, create a layout template and add `tagging: [collection id]` in FM. As a result you will have access to a new derived collection named `tagCollection`:

templates/layouts/tag.html:
{|
```html
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

templates/layouts/tag.html:
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

### Accessing tags

     
There is also a `site.tags` property, which enables this syntax in templates:

{|
```html
{#for entry in site.tags}
    {#for entry in site.tags}
        Tag: {entry.key} has {entry.value.size} pages
    {/for} 
{/for}

```
|}

Or with sorting:

     {|
```html
{#let tag_words=site.tags.entrySet.sort('key')}
    {#for entry in tag_words}
        <a href="/blog/tag/{entry.key}">{entry.key}</a> ({entry.value.size})
    {/for}
{/let}
```
|}


     

### Template Extensions

| Usage                  | Description                                                                            |
|------------------------|----------------------------------------------------------------------------------------|
| `collection.allTags`   | Returns a list of all tags from the collection, each tag slugified                     |
| `collection.tagsCount` | Returns a list of all tags slugified (name) with their count (count) in the collection |
| `site.tags`            | Returns a map of all tags in the site and pages with that tag, each tag slugified      |
