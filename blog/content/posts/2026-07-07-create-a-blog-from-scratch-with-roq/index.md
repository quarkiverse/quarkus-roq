---
title: "Create a Blog from Scratch with Roq (45min)"
slug: create-a-blog-from-scratch-with-roq
description: "Step-by-step tutorial: build a blog from scratch with Roq using the base theme. Learn layouts, collections, and Tailwind styling."
author: ia3andy
qute: false
tags: [tutorial]
series: roq-blog-lab
date: 2026-07-05 11:00
image: https://images.unsplash.com/photo-1604716053460-3f66248bf8de?q=80&w=1200&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
---

The [first tutorial](/posts/create-a-blog-with-roq/) showed you how to create a blog using the default theme. Everything was styled and ready to go. But what if you want to understand how it all works under the hood? What if you want full control over every layout, every class, every pixel?

That's what this tutorial is about. You'll build a blog from scratch using Roq's **base theme**, which gives you the foundation (SEO, favicon, bundling) but zero styling. You'll create your own layouts, wire up collections, add pagination and tags, all while learning how Roq's template system works.

> [!NOTE]
> **Prerequisites:** Install the Roq CLI by following the [Getting Started guide](/docs/getting-started/). Roq uses JBang, so no JDK installation is needed.
> Verify your setup with:
> ```
> roq --version
> ```

> [!TIP]
> For the best development experience, install the [Quarkus IDE tooling](https://quarkus.io/guides/ide-tooling) for your editor (VS Code, IntelliJ, or Eclipse). You get config autocompletion, validation, and Qute template completion.


## 1. Create the project

Create a new Roq project with the **base theme** (no pre-built styling):

```shell
roq create my-blog -x theme:base
```

The base theme provides three things: `{#seo /}` for meta tags, `{#favicon /}` for favicon discovery, and `{#bundle /}` for CSS/JS bundling. Everything else is up to you.

Now add Tailwind CSS:

```shell
cd my-blog
roq add web:tailwindcss
```

Start dev mode:

```shell
roq start
```

🚀 Hit `w` or open [http://localhost:8080](http://localhost:8080). You should see a basic page with minimal styling and some placeholder content. We'll replace all of it.


## 2. 👀 Explore the base theme

In Roq, a theme is a Maven dependency that provides layouts, partials, styles, and much more. Your project already includes the base theme as a dependency in `pom.xml`. You can browse its source on [GitHub](https://github.com/quarkiverse/quarkus-roq/tree/main/roq-frontmatter/runtime/src/main/resources/templates/theme-layouts/roq-base). It provides three layouts you can extend:

- **`default`**: the HTML skeleton with `<head>` (SEO, favicon, bundle) and `<body>`. This is the root of the layout chain.
- **`page`**: extends `default`, adds a simple `<main>` with an `<h1>` title. For generic pages.
- **`post`**: extends `default`, same as `page` but adds the post date. For blog posts.

These layouts are intentionally minimal. They use `{#insert /}` as a content slot. When your page says `layout: default`, Roq injects your page content into that slot.

🚀🔑 The layout chain works like inheritance: your content page declares a `layout`, that layout can declare its own parent `layout`, all the way up to the base `default`. Each level wraps the previous one.


## 3. Create your default layout

The base theme's `default` layout works, but it has no visual structure. Let's create our own that extends it and adds a site header, navigation, and footer.

**››› CODING TIME**

Create `templates/layouts/default.html` that extends the base theme's default. Add Tailwind classes for a slate/sky color scheme, a header with your site name, and a footer.

<details>
<summary>See hint</summary>

Use `theme-layout: default` in the frontmatter to extend the base theme's default (instead of `layout: default` which would create a loop). The base theme's `{#insert head /}` slot lets you add extra content to `<head>`. The main `{#insert /}` slot is where your body content goes. Add a `<header>`, `<main>{#insert /}</main>`, and `<footer>`.

</details>

<details>
<summary>See solution</summary>

Create `templates/layouts/default.html`:

```html
---
theme-layout: default
---
{@io.quarkiverse.roq.frontmatter.runtime.model.Page page}
{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}

<div class="min-h-screen bg-slate-50 dark:bg-slate-900 text-slate-800 dark:text-slate-200">
  <header class="border-b border-slate-200 dark:border-slate-800">
    <div class="max-w-3xl mx-auto px-4 py-4 flex items-center justify-between">
      <a href="/" class="text-lg font-bold text-slate-900 dark:text-white hover:text-sky-600 dark:hover:text-sky-400 transition-colors">
        {site.title}
      </a>
      <nav class="flex gap-4 text-sm">
        <a href="/" class="text-slate-600 dark:text-slate-400 hover:text-sky-600 dark:hover:text-sky-400 transition-colors">Home</a>
        <a href="/about" class="text-slate-600 dark:text-slate-400 hover:text-sky-600 dark:hover:text-sky-400 transition-colors">About</a>
      </nav>
    </div>
  </header>

  <main class="max-w-3xl mx-auto px-4 py-8">
    {#insert /}
  </main>

  <footer class="border-t border-slate-200 dark:border-slate-800 mt-12">
    <div class="max-w-3xl mx-auto px-4 py-6 text-center text-sm text-slate-500 dark:text-slate-400">
      Built with <a href="https://iamroq.dev" class="text-sky-600 dark:text-sky-400 hover:underline">Roq</a>
    </div>
  </footer>
</div>
```

</details>

🚀 Refresh your browser. The page now has a header with your site name, a navigation bar, and a footer. The content area is centered at `max-w-3xl`.

🚀🔑 Notice the `theme-layout: default` in the frontmatter. This tells Roq to extend the *base theme's* default layout directly, not your own. Without `theme-layout:`, using `layout: default` would create a self-referencing loop. This is how you override a theme layout while still inheriting its `<head>` setup (SEO, favicon, bundle).


## 4. Set up the CSS

The Tailwind extension replaced `web/app.css` with Tailwind imports. Let's also add the Tailwind typography plugin for styling prose content (blog posts rendered from Markdown).

**››› CODING TIME**

Open `web/app.css` and make sure it imports Tailwind and the typography plugin.

<details>
<summary>See hint</summary>

The file should already have `@import "tailwindcss"`. Add `@plugin "@tailwindcss/typography"` for the `prose` class that styles rendered HTML from Markdown.

</details>

<details>
<summary>See solution</summary>

Edit `web/app.css`:

```css
@import "tailwindcss";
@plugin "@tailwindcss/typography";
```

</details>

🚀 Save and verify the page still loads with Tailwind classes applied.


## 5. Create the home page

Let's replace the placeholder index with a proper home page that will later show a list of blog posts.

**››› CODING TIME**

Replace `content/index.html` with a home page that has a title, a short intro, and a placeholder for the blog listing (we'll add that after creating some posts).

<details>
<summary>See hint</summary>

Set `layout: default` and give it a `title` and `description` in the frontmatter. The `title` from your index page becomes `site.title`, which the header already displays. Use Tailwind classes for spacing and typography.

</details>

<details>
<summary>See solution</summary>

Replace `content/index.html`:

```html
---
layout: default
title: My Blog
description: A blog built from scratch with Roq and Tailwind CSS.
---

<div class="space-y-6">
  <div class="text-center space-y-2">
    <h1 class="text-3xl font-bold text-slate-900 dark:text-white">Welcome</h1>
    <p class="text-slate-500 dark:text-slate-400">Thoughts on code, craft, and everything in between.</p>
  </div>

  <section>
    <h2 class="text-xl font-semibold text-slate-800 dark:text-slate-100 mb-4">Latest posts</h2>
    <p class="text-slate-500 dark:text-slate-400 italic">No posts yet. Create one and come back!</p>
  </section>
</div>
```

</details>

Also create a simple about page. Create `content/about.md`:

```markdown
---
layout: default
title: About
---

# About this blog

This blog is built from scratch with [Roq](https://iamroq.dev) and [Tailwind CSS](https://tailwindcss.com). No pre-built theme, just custom layouts and Markdown content.
```

🚀 Check the home page and click "About" in the nav. Both pages should render with your layout.


## 6. Create the post layout

Before writing blog posts, we need a layout that knows how to display them: title, date, tags, and the rendered Markdown content.

**››› CODING TIME**

Create `templates/layouts/post.html` that extends your `default` layout. Display the post title, date, tags, and content. Use the `prose` class from Tailwind Typography to style the Markdown output.

<details>
<summary>See hint</summary>

Use `layout: default` to inherit your custom layout. Declare the page type as `{@io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage page}` (DocumentPage, not Page, because posts are collection documents with extra fields like `date`). Access `page.title`, `page.date.longDate`, `page.data.tags.asStrings`. The content slot `{#insert /}` renders the Markdown body. Wrap it in `<div class="prose dark:prose-invert">` for typography styling.

</details>

<details>
<summary>See solution</summary>

Create `templates/layouts/post.html`:

```html
---
layout: default
---
{@io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage page}
{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}

<article class="space-y-6">
  <header class="space-y-2">
    <h1 class="text-3xl font-bold text-slate-900 dark:text-white">{page.title}</h1>
    {#if page.date}
    <time class="text-sm text-slate-500 dark:text-slate-400">{page.date.longDate}</time>
    {/if}
    {#if page.data.tags}
    <div class="flex gap-2">
      {#for tag in page.data.tags.asStrings}
      <span class="text-xs bg-sky-100 dark:bg-sky-900 text-sky-700 dark:text-sky-300 px-2 py-0.5 rounded">{tag}</span>
      {/for}
    </div>
    {/if}
  </header>

  <div class="prose dark:prose-invert max-w-none">
    {#insert /}
  </div>

  <footer class="border-t border-slate-200 dark:border-slate-800 pt-4 flex justify-between text-sm text-slate-500 dark:text-slate-400">
    {#if page.previous}
    <a href="{page.previous.url}" class="hover:text-sky-600 dark:hover:text-sky-400">&larr; {page.previous.title}</a>
    {#else}<span></span>{/if}
    {#if page.next}
    <a href="{page.next.url}" class="hover:text-sky-600 dark:hover:text-sky-400">{page.next.title} &rarr;</a>
    {/if}
  </footer>
</article>
```

</details>

🚀🔑 The `{@io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage page}` declaration is important. A `DocumentPage` is a page that belongs to a collection (like `posts`). It has extra fields: `date`, `next`, `previous`, `collectionId`. A regular `Page` doesn't have these.


## 7. Configure the posts collection

Roq needs to know that `content/posts/` is a collection of blog posts. This is configured in `application.properties`.

**››› CODING TIME**

Open `config/application.properties` and add the posts collection configuration.

<details>
<summary>See hint</summary>

Use `site.collections.posts.layout=post` to tell Roq that files in `content/posts/` should use the `post` layout. The collection name (`posts`) matches the directory name by convention.

</details>

<details>
<summary>See solution</summary>

Edit `config/application.properties`:

```properties
site.collections.posts.layout=post
```

</details>


## 8. Write your first post

Time to create some content. Blog posts live in `content/posts/` and follow a date-based naming convention.

**››› CODING TIME**

Create your first post at `content/posts/2026-07-01-hello-world/index.md` with a title, description, tags, and some Markdown content.

<details>
<summary>See hint</summary>

The directory name `2026-07-01-hello-world` gives Roq the date and slug. Add frontmatter with `title`, `description`, and `tags`. You don't need to specify `layout` because the collection config already sets it to `post`. Write a few paragraphs of Markdown.

</details>

<details>
<summary>See solution</summary>

Create `content/posts/2026-07-01-hello-world/index.md`:

```markdown
---
title: "Hello, World!"
description: "My very first blog post, built from scratch with Roq."
tags: [hello, roq]
---

## Welcome

This is my first blog post. I built this blog from scratch using [Roq](https://iamroq.dev) and Tailwind CSS.

No pre-built theme. Just custom layouts, a posts collection, and Markdown content. Here's what I learned:

- **Layouts are just HTML with Qute tags.** You extend them with `layout:` in frontmatter.
- **Collections are directories.** Drop a Markdown file in `content/posts/` and it becomes a blog post.
- **Live-reload is instant.** Save the file, see the result.

More posts coming soon!
```

</details>

Create a second post so we have something to paginate later:

```markdown
---
title: "Learning Roq Layouts"
description: "How template inheritance works in Roq."
tags: [roq, layouts]
---

## The layout chain

Every page in Roq goes through a layout chain. Your content page declares a `layout`, that layout can declare its own parent `layout`, and so on up to the base `default`.

This is how you get consistent headers, navs, and footers across your entire site without repeating yourself.
```

Save this as `content/posts/2026-07-02-learning-roq-layouts/index.md`.

🚀 Navigate to `/hello-world/` and `/learning-roq-layouts/`. You should see your posts rendered with the title, date, tags, and prose-styled content. The previous/next links at the bottom should connect them.


## 9. Add the blog listing

Now let's update the home page to list your posts with pagination.

**››› CODING TIME**

Update `content/index.html` to iterate over the posts collection and display each post as a card with title, date, description, and a link.

<details>
<summary>See hint</summary>

Add `paginate: posts` to the frontmatter to enable pagination. Then use `{#for post in site.collections.posts.paginated(page.paginator)}` to loop through posts. Access `post.title`, `post.date.longDate`, `post.description`, `post.url`. For pagination links, use `{#include fm/pagination.html /}`.

</details>

<details>
<summary>See solution</summary>

Replace `content/index.html`:

```html
---
layout: default
title: My Blog
description: A blog built from scratch with Roq and Tailwind CSS.
paginate:
  collection: posts
  size: 5
---

<div class="space-y-8">
  <div class="text-center space-y-2">
    <h1 class="text-3xl font-bold text-slate-900 dark:text-white">My Blog</h1>
    <p class="text-slate-500 dark:text-slate-400">Thoughts on code, craft, and everything in between.</p>
  </div>

  <div class="space-y-4">
    {#for post in site.collections.posts.paginated(page.paginator)}
    <a href="{post.url}" class="block p-5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-sky-400 dark:hover:border-sky-500 hover:shadow-md transition-all duration-200">
      <h2 class="text-lg font-semibold text-slate-900 dark:text-white">{post.title}</h2>
      <time class="text-xs text-slate-500 dark:text-slate-400">{post.date.longDate}</time>
      {#if post.description}
      <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">{post.description}</p>
      {/if}
    </a>
    {/for}
  </div>

  {#include fm/pagination.html /}
</div>
```

</details>

🚀 Go to the home page. Your posts should appear as clickable cards, sorted by date (newest first). With only two posts you won't see pagination yet, but the mechanism is in place.

🤩 You've built a working blog from scratch. Posts, layouts, a listing page with pagination. All with Tailwind and zero pre-built theme code.


## 10. Add tag support

Tags let readers browse posts by topic. The tagging plugin auto-generates a page for each tag.

```shell
roq add plugin:tagging
```

**››› CODING TIME**

Create `templates/layouts/tag.html` that displays all posts for a given tag, with pagination.

<details>
<summary>See hint</summary>

Use `tagging: posts` and `paginate: true` in the frontmatter to wire up the tag page. The current tag is in `page.data.tag`. The tagged posts are in `site.collections.get(page.data.tagCollection)`. Use `.paginated(page.paginator)` for pagination.

</details>

<details>
<summary>See solution</summary>

Create `templates/layouts/tag.html`:

```html
---
layout: default
tagging: posts
paginate: true
---
{@io.quarkiverse.roq.frontmatter.runtime.model.NormalPage page}
{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}

<div class="space-y-6">
  <div class="text-center space-y-2">
    <h1 class="text-2xl font-bold text-slate-900 dark:text-white">Tag: {page.data.tag}</h1>
  </div>

  <div class="space-y-4">
    {#for post in site.collections.get(page.data.tagCollection).paginated(page.paginator)}
    <a href="{post.url}" class="block p-5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-sky-400 dark:hover:border-sky-500 hover:shadow-md transition-all duration-200">
      <h2 class="text-lg font-semibold text-slate-900 dark:text-white">{post.title}</h2>
      <time class="text-xs text-slate-500 dark:text-slate-400">{post.date.longDate}</time>
    </a>
    {/for}
  </div>

  {#include fm/pagination.html /}
</div>
```

</details>

Now update the tag spans in your `post.html` layout to be clickable links:

<details>
<summary>See hint</summary>

Replace the `<span>` tags with `<a>` links pointing to `{site.url('/posts/tag', tag.slugify)}`.

</details>

<details>
<summary>See solution</summary>

In `templates/layouts/post.html`, replace the tags section:

```html
    {#if page.data.tags}
    <div class="flex gap-2">
      {#for tag in page.data.tags.asStrings}
      <a href="{site.url('/posts/tag', tag.slugify)}" class="text-xs bg-sky-100 dark:bg-sky-900 text-sky-700 dark:text-sky-300 px-2 py-0.5 rounded hover:bg-sky-200 dark:hover:bg-sky-800 transition-colors">{tag}</a>
      {/for}
    </div>
    {/if}
```

</details>

🚀 Click on a tag in a blog post. You should land on a page like `/posts/tag/roq/` listing all posts with that tag.


## 11. Add an RSS feed

RSS lets readers subscribe to your blog. Roq has built-in support.

**››› CODING TIME**

Create `content/rss.xml` and add the RSS auto-discovery tag to your default layout.

<details>
<summary>See hint</summary>

Create `content/rss.xml` with `{#include fm/rss.html /}` inside. Then add `{#rss site /}` in your `default.html` layout, inside the `{#head}` insert block, to add the `<link rel="alternate" type="application/rss+xml">` tag to the page head.

</details>

<details>
<summary>See solution</summary>

Create `content/rss.xml`:

```html
---
---
{#include fm/rss.html /}
```

Update `templates/layouts/default.html` to add RSS discovery. Add this block after the opening `---`:

```html
{#head}
{#rss site /}
{/head}
```

So the top of your `default.html` becomes:

```html
---
theme-layout: default
---
{@io.quarkiverse.roq.frontmatter.runtime.model.Page page}
{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}

{#head}
{#rss site /}
{/head}

<div class="min-h-screen bg-slate-50 dark:bg-slate-900 ...">
```

</details>

🚀 Navigate to `/rss.xml` in your browser. You should see a valid RSS feed with your blog posts.


## 12. Deploy to GitHub Pages

Your project already includes a `.github/workflows/deploy.yml` file that handles deployment.

**››› CODING TIME**

Push your blog to GitHub and enable GitHub Pages.

<details>
<summary>See hint</summary>

Create a repository on GitHub, push your code, then go to Settings > Pages and set the source to "GitHub Actions".

</details>

<details>
<summary>See solution</summary>

```shell
git init
git add .
git commit -m "Initial blog"
gh repo create my-blog --public --source=. --push
```

Then in your repository settings:
1. Go to **Settings** > **Pages**
2. Set Source to **GitHub Actions**

Your blog will be live at `https://your-username.github.io/my-blog/` within a couple of minutes.

</details>

🚀 The first run will likely fail because GitHub Pages is not yet enabled. Go to **Settings** > **Pages**, set Source to **GitHub Actions**, then re-run the workflow from the **Actions** tab. Once it passes, visit your live URL.

🤩 You built a blog from scratch. Custom layouts, a posts collection, pagination, tags, RSS, and deployment. All from a blank base theme, styled with Tailwind, and deployed in one push.


## What's next?

Here are a few ideas to keep going:

- **Add more posts**: create new directories in `content/posts/` or use the Editor (press `m` in the dev terminal).
- **Add a sitemap**: `roq add plugin:sitemap` and it's done.
- **Add search**: `roq add plugin:lunr` for full-text search. You'll need to add three tags to your `default.html` layout:

  <details>
  <summary>See hint</summary>

  Add `{#search-script /}` in the `{#head}` slot, `{#search-overlay /}` at the top of the body, and `{#search-button /}` in your nav bar. See the [Lunr Search plugin docs](/plugin/lunr-search/) for details.

  </details>

  <details>
  <summary>See solution</summary>

  In `templates/layouts/default.html`, add:

  ```html
  {#head}{#search-script /}{/head}

  {#search-overlay /}
  ```

  And in your `<nav>`:

  ```html
  <nav class="flex gap-4 text-sm items-center">
    <a href="/" ...>Home</a>
    <a href="/about" ...>About</a>
    {#search-button /}
  </nav>
  ```

  </details>

  Press **Cmd+K** (or **Ctrl+K**) to try it out.
- **Add images to posts**: drop an image in the post directory and set `image: photo.jpg` in frontmatter.
- **Dark mode toggle**: the CSS classes are already in place (`dark:`), add a JavaScript toggle button.
- **Explore the docs**: [Roq the basics](/docs/basics/) covers collections, custom data, templates, and much more.

Happy building!
