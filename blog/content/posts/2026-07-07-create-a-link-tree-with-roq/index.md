---
title: "Create a Link-Tree with Roq (45min)"
slug: create-a-link-tree-with-roq
description: "Step-by-step tutorial: build a personal link-tree site from scratch with Roq."
author: ia3andy
qute: false
tags: [tutorial]
series: roq-blog-lab
date: 2026-07-05 12:00
image: https://images.unsplash.com/photo-1528183429752-a97d0bf99b5a?q=80&w=1200&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
---

Link-tree sites are everywhere: a clean page with your photo, a few links, maybe some social icons. Simple enough that you could build one in plain HTML, but what if you want multiple link pages, QR codes, and zero-effort deployment?

In this tutorial you'll build a personal link-tree from scratch with [Roq](https://iamroq.dev) and [Tailwind CSS](https://tailwindcss.com). No pre-built theme, just you, data files, layouts, and reusable template components. By the end you'll have a polished site with typed data, icon sets, auto-generated pages, and downloadable QR codes.

> [!NOTE]
> **Prerequisites:** Install the Roq CLI by following the [Getting Started guide](/docs/getting-started/). Roq uses JBang, so no JDK installation is needed.
> Verify your setup with:
> ```
> roq --version
> ```

> [!TIP]
> For the best development experience, install the [Quarkus IDE tooling](https://quarkus.io/guides/ide-tooling) for your editor (VS Code, IntelliJ, or Eclipse). You get config autocompletion, validation, and Qute template completion.

> [!TIP]
> The finished project is available at [github.com/ia3andy/roq-linktree-tuto](https://github.com/ia3andy/roq-linktree-tuto) if you get stuck.


## 1. Create the project

Open a terminal and create a new Roq project with the **base theme** (no styling, just the essentials):

```shell
roq create my-linktree -x theme:base
```

The base theme gives you SEO tags, favicon support, and CSS/JS bundling, but no visual styling. That's intentional: we'll bring our own with Tailwind.

```shell
cd my-linktree
```

Now add the Tailwind CSS extension:

```shell
roq add web:tailwindcss
```

Start dev mode:

```shell
roq start
```

🚀 Hit `w` or open [http://localhost:8080](http://localhost:8080). You should see a bare-bones page with no styling. That's expected, we'll fix that next.

Here's what was generated:

```
my-linktree/
├── content/              # Your pages
│   └── index.html        # Home page
├── data/                 # Data files (YAML/JSON)
├── public/               # Static assets (images, favicon…)
├── web/                  # CSS and JS (bundled automatically)
│   └── app.css
└── pom.xml
```

- **`content/`** is where you write your pages.
- **`data/`** holds structured data (YAML/JSON) accessible from templates. We'll use this for profile and links.
- **`web/`** is for CSS and JS, bundled automatically by Web Bundler.
- **`public/`** holds static files served as-is (images, fonts).
- **`templates/`** doesn't exist yet, but this is where you'll create your own [layouts](/docs/basics/#layouts), [partials](/docs/basics/#partials), and [tags](/docs/basics/#tags) to override or extend the theme.


## 2. Set up Tailwind and component styles

The base theme already provides a `default` layout with the HTML skeleton, SEO tags, favicon, and `{#bundle /}`. We need to set up our CSS with Tailwind, icon imports, and reusable component classes.

First, add the [Phosphor Icons](https://phosphoricons.com/) npm dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.mvnpm.at.phosphor-icons</groupId>
    <artifactId>web</artifactId>
    <version>2.1.2</version>
    <scope>provided</scope>
</dependency>
```

Then replace the contents of `web/app.css` with Tailwind, icon imports, and all the component styles we'll use throughout the tutorial. Instead of writing Tailwind utility classes directly in the HTML, we define semantic class names using `@apply` to keep our templates clean and maintainable:

<details>
<summary>See the full CSS</summary>

```css
@import "tailwindcss";
@import "@phosphor-icons/web/regular";
@import "@phosphor-icons/web/fill";

/* Layout */
.lt-page {
  @apply flex items-start justify-center pb-6 pt-12;
}

.lt-page-relative {
  @apply flex items-start justify-center pb-6 pt-12 relative;
}

.lt-container {
  @apply w-full max-w-md mx-auto space-y-8 px-4;
}

/* Profile */
.lt-profile {
  @apply text-center space-y-3;
}

.lt-avatar {
  @apply w-32 h-32 mx-auto rounded-full bg-white dark:bg-slate-800 p-2 shadow-md;
}

.lt-name {
  @apply text-2xl font-bold tracking-tight;
}

.lt-name-text {
  @apply text-slate-900 dark:text-white;
}

.lt-handle {
  @apply text-sky-500 dark:text-sky-400;
}

.lt-title {
  @apply text-sm text-slate-500 dark:text-slate-400;
}

/* Social */
.lt-social {
  @apply text-center space-y-3;
}

.lt-social-icons {
  @apply flex items-center justify-center gap-5;
}

.lt-social-link {
  @apply text-slate-500 dark:text-slate-400 hover:text-sky-600 dark:hover:text-sky-400 transition-colors;
}

/* Link cards */
.lt-links {
  @apply space-y-3;
}

.lt-link-card {
  @apply flex items-center justify-between px-5 py-4 rounded-lg
         border border-slate-200 dark:border-slate-700
         bg-white dark:bg-slate-800
         hover:border-sky-400 dark:hover:border-sky-500
         hover:shadow-md
         transition-all duration-200;
}

.lt-link-content {
  @apply flex items-center gap-3;
}

.lt-link-icon {
  @apply text-sky-500 dark:text-sky-400;
}

.lt-link-name {
  @apply text-sm font-medium text-slate-800 dark:text-slate-100;
}

.lt-link-desc {
  @apply block text-xs text-slate-500 dark:text-slate-400;
}

.lt-link-arrow {
  @apply text-slate-300 dark:text-slate-600 transition-colors;
}

.group:hover .lt-link-arrow {
  @apply text-sky-500 dark:text-sky-400;
}

/* Profile links separator */
.lt-profile-links {
  @apply pt-2 pb-10 space-y-3;
}

.lt-separator {
  @apply text-center text-slate-300 dark:text-slate-600;
}

/* Trees gallery */
.lt-trees {
  @apply space-y-6;
}

.lt-tree-card {
  @apply text-center space-y-4 p-5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800;
}

.lt-tree-title {
  @apply text-lg font-bold text-slate-800 dark:text-slate-100;
}

.lt-tree-desc {
  @apply text-xs text-slate-500 dark:text-slate-400;
}

.lt-qr-wrap {
  @apply inline-block p-3 bg-white rounded-xl;
}

.lt-tree-actions {
  @apply flex items-center justify-center gap-4;
}

.lt-tree-action {
  @apply text-sm text-sky-600 dark:text-sky-400 hover:text-sky-800 dark:hover:text-sky-200 transition-colors;
}

.lt-tree-action-btn {
  @apply text-sm text-sky-600 dark:text-sky-400 hover:text-sky-800 dark:hover:text-sky-200 transition-colors cursor-pointer;
}

/* Home link */
.lt-home-link {
  @apply flex items-center justify-center gap-2 text-base text-slate-500 dark:text-slate-400 hover:text-sky-600 dark:hover:text-sky-400 transition-colors;
}

/* Navigation */
.lt-nav-trees {
  @apply absolute top-4 right-4 text-slate-400 dark:text-slate-500 hover:text-sky-500 dark:hover:text-sky-400 transition-colors;
}

/* Section heading */
.lt-heading {
  @apply text-center space-y-2;
}

.lt-heading-title {
  @apply text-2xl font-bold tracking-tight text-slate-900 dark:text-white;
}
```

</details>

> [!NOTE]
> Replacing the CSS will break the look of the initial content from the codestart. That's expected: we build our own design in the following steps.

🚀 Refresh your browser to make sure the CSS compiles without errors.

🚀🔑 The `{#bundle /}` tag is the magic glue. It tells Roq's Web Bundler to compile everything in `web/` (CSS, JS, npm packages) and inject it into the page. Tailwind is processed at build time, so only the classes you actually use end up in the final CSS. The `@apply` directives let us define semantic class names (`lt-avatar`, `lt-link-card`, etc.) that map to Tailwind utilities, keeping our templates clean.


## 3. Create the profile data

Link-tree sites are all about *you*. Let's define your profile as structured data that templates can access. The profile includes your info, social links, and your main set of links.

**››› CODING TIME**

Create `data/profile.yml` with your name, handle, title, bio, profile image, a list of social links, and a list of links. Each social entry should have a `name`, `url`, and `icon`. Each link should have a `name`, `url`, `description`, and `icon`.

<details>
<summary>See hint</summary>

YAML files in `data/` are automatically available in templates via `cdi:` prefix. For the icon fields, we'll use [Phosphor Icons](https://phosphoricons.com/) names. Pick names like `github-logo`, `linkedin-logo`, `butterfly` (for Bluesky), `lightning`, `terminal`, etc.

</details>

<details>
<summary>See solution</summary>

Create `data/profile.yml`:

```yaml
name: Groot
handle: "@iamgroot"
title: I am Groot.
image: groot.png
bio: I am Groot. I am Groot. I am Groot!
social:
  - name: GitHub
    url: https://github.com/iamgroot
    icon: github-logo
  - name: LinkedIn
    url: https://www.linkedin.com/in/iamgroot
    icon: linkedin-logo
  - name: Bluesky
    url: https://bsky.app/profile/iamgroot.bsky.social
    icon: butterfly
links:
  - name: Guardians HQ
    url: https://marvel.com/guardians
    description: Where we save the galaxy
    icon: shield-star
  - name: Quarkus
    url: https://quarkus.io
    description: Supersonic Subatomic Java framework
    icon: lightning
  - name: Roq
    url: https://iamroq.dev
    description: Static site generator with a Java soul
    icon: terminal
  - name: Tree Care Tips
    url: https://en.wikipedia.org/wiki/Groot
    description: I am Groot
    icon: tree
```

Drop a profile image as `public/images/groot.png` (or any image you like).

</details>


## 4. Map the profile data to Java

Raw YAML data works in templates, but Roq can do better. With `@DataMapping`, you map your YAML to a typed Java record. This gives you compile-time safety and auto-completion.

**››› CODING TIME**

Create `src/main/java/io/acme/Profile.java` as a Java record annotated with `@DataMapping("profile")`. Include fields for `name`, `handle`, `title`, `bio`, `image`, a `List<Social>` and a `List<Link>`.

<details>
<summary>See hint</summary>

`@DataMapping("profile")` tells Roq to map `data/profile.yml` to this record. The field names must match the YAML keys. For nested lists, use nested record types. Import from `io.quarkiverse.roq.data.runtime.annotations.DataMapping`.

</details>

<details>
<summary>See solution</summary>

Create `src/main/java/io/acme/Profile.java`:

```java
package io.acme;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import java.util.List;

@DataMapping("profile")
public record Profile(String name, String handle, String title, String bio,
                       String image, List<Social> social, List<Link> links) {

    public record Social(String name, String url, String icon) {}

    public record Link(String name, String url, String description, String icon) {}
}
```

</details>

🚀🔑 The `@DataMapping` annotation is the bridge between your YAML data and your templates. Once mapped, you access the profile in templates with `cdi:profile.name`, `cdi:profile.handle`, etc. The `cdi:` prefix means it's a CDI bean, which is how Quarkus manages dependency injection.


## 5. Create reusable template components

Before building the full page, let's create two reusable components: a **profile partial** (avatar, name, social icons) and a **linkCard tag** (a single link card). We'll reuse these across all our layouts.

**››› CODING TIME**

Create `templates/partials/profile.html` that displays the avatar, name, handle, title, and social icons from `cdi:profile`. Use the `lt-*` CSS classes from step 2.

<details>
<summary>See hint</summary>

Partials are included with `{#include partials/profile /}` and share the parent template's context. Use `site.image(cdi:profile.image)` for the avatar URL. Loop through `cdi:profile.social` for the social icons with `ph-fill ph-{s.icon}` classes.

</details>

<details>
<summary>See solution</summary>

Create `templates/partials/profile.html`:

```html
<div class="lt-profile">
  {#if cdi:profile.image}
  <img src="{site.image(cdi:profile.image)}" alt="{cdi:profile.name}" class="lt-avatar">
  {/if}
  <h1 class="lt-name">
    {#if cdi:profile.name}<span class="lt-name-text">{cdi:profile.name}</span>{/if}
    {#if cdi:profile.handle}<span class="lt-handle">{#if cdi:profile.name} {/if}{cdi:profile.handle}</span>{/if}
  </h1>
  <p class="lt-title">{cdi:profile.title}</p>
</div>

{#if cdi:profile.social??}
<div class="lt-social">
  <div class="lt-social-icons">
    {#for s in cdi:profile.social}
    <a href="{s.url}" target="_blank" rel="noopener noreferrer" aria-label="{s.name}" class="lt-social-link">
      <i class="ph-fill ph-{s.icon}" style="font-size: 24px;"></i>
    </a>
    {/for}
  </div>
</div>
{/if}
```

</details>

Now create the link card tag.

**››› CODING TIME**

Create `templates/tags/linkCard.html` that renders a single link as a styled card with an icon, name, description, and arrow.

<details>
<summary>See hint</summary>

Tags are called with `{#linkCard link=myLink /}`. The `link` variable is passed as a parameter. Use the `group` class on the `<a>` element alongside `lt-link-card` for the hover effect on the arrow.

</details>

<details>
<summary>See solution</summary>

Create `templates/tags/linkCard.html`:

```html
<a href="{link.url}" target="_blank" rel="noopener noreferrer" class="group lt-link-card">
  <span class="lt-link-content">
    {#if link.icon}
    <i class="ph ph-{link.icon} lt-link-icon" style="font-size: 20px;"></i>
    {/if}
    <span>
      <span class="lt-link-name">{link.name}</span>
      <span class="lt-link-desc">{link.description}</span>
    </span>
  </span>
  <span class="lt-link-arrow">
    <i class="ph ph-arrow-right" style="font-size: 16px;"></i>
  </span>
</a>
```

</details>

🚀🔑 Partials share the calling template's context (they can access `site`, `page`, etc.), while tags receive data explicitly through parameters. Use partials for sections that always render the same way, and tags for components you call multiple times with different data.


## 6. Build the home page

Now let's create a layout for the home page. The `linktree-home` layout shows your profile, social icons, and your links from the profile data. It also adds a navigation icon to the trees gallery.

**››› CODING TIME**

Create `templates/layouts/linktree-home.html` that extends `default`. It should display the profile partial, loop through `cdi:profile.links` using the linkCard tag, and include a navigation icon to `/trees`.

<details>
<summary>See hint</summary>

Use `layout: default` in the frontmatter to extend the base layout. Include the profile with `{#include partials/profile /}` and loop through links with `{#for link in cdi:profile.links}{#linkCard link=link /}{/for}`. Use `lt-page-relative` and `lt-nav-trees` for the trees icon. The `{#insert /}` slot lets content pages add extra content.

</details>

<details>
<summary>See solution</summary>

Create `templates/layouts/linktree-home.html`:

```html
---
layout: default
---
{@io.quarkiverse.roq.frontmatter.runtime.model.Page page}
{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}

<div class="lt-page-relative">
  <a href="/trees" class="lt-nav-trees" title="All trees">
    <i class="ph ph-tree-structure" style="font-size: 24px;"></i>
  </a>
  <div class="lt-container">

    {#include partials/profile /}

    {#if cdi:profile.links??}
    <div class="lt-links">
      {#for link in cdi:profile.links}
      {#linkCard link=link /}
      {/for}
    </div>
    {/if}

    {#insert /}

  </div>
</div>
```

</details>

Now update your home page to use this layout. Replace the content of `content/index.html`:

```html
---
layout: linktree-home
title: Groot
---
```

🚀 Refresh your browser. You should see your full link-tree: avatar in a circle, name in dark text, handle in sky blue, social icons with hover effects, and a list of link cards with icons and arrows. All from a three-line content file!

🤩 That's a complete link-tree, and it's all driven by YAML data. Want to change a link? Edit `data/profile.yml`. Want to add one? Add a line. No template changes needed.



## 7. Create a secondary tree

Your home page shows your main links from the profile. But what if you want a separate link-tree for a specific topic? You can create additional trees as YAML files in `data/trees/`. Why a directory instead of a single file? Because you might want multiple link-trees: one for personal links, one for work, one for a specific event or conference. Each YAML file in `data/trees/` becomes its own page with its own QR code (we'll set that up in steps 9 and 10).

**››› CODING TIME**

Create `data/trees/guardians.yml` with a title, description, and a list of links related to a different topic.

<details>
<summary>See solution</summary>

Create `data/trees/guardians.yml`:

```yaml
title: Guardians of the Galaxy
description: I am Groot's team resources
links:
  - name: Guardians HQ
    url: https://marvel.com/guardians
    description: Where we save the galaxy
    icon: shield-star
  - name: Milano Ship Manual
    url: https://en.wikipedia.org/wiki/Guardians_of_the_Galaxy
    description: How to fly the Milano
    icon: rocket
  - name: Groot's Wikipedia
    url: https://en.wikipedia.org/wiki/Groot
    description: Everything about me
    icon: tree
```

</details>


## 8. Map the trees data to Java

Just like the profile, we'll map the trees data to a typed Java record. The difference is that trees live in a *directory* (`data/trees/`), so we use `DataMapping.Type.OBJECT_DIR` to load all files in that directory as a map.

**››› CODING TIME**

Create `src/main/java/io/acme/Trees.java` with `@DataMapping(value = "trees", type = DataMapping.Type.OBJECT_DIR)`. The record should contain a `Map<String, Tree>` where each key is the file name (e.g. `guardians`) and `Tree` has `title`, `description`, and `List<Link>`.

<details>
<summary>See hint</summary>

Use `DataMapping.Type.OBJECT_DIR` to tell Roq that `data/trees/` is a directory of objects, not a single file. The map key comes from the file name. `Tree` and `Link` are nested records inside `Trees`.

</details>

<details>
<summary>See solution</summary>

Create `src/main/java/io/acme/Trees.java`:

```java
package io.acme;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import java.util.List;
import java.util.Map;

@DataMapping(value = "trees", type = DataMapping.Type.OBJECT_DIR)
public record Trees(Map<String, Tree> map) {

    public record Tree(String title, String description, List<Link> links) {}

    public record Link(String name, String url, String description, String icon) {}
}
```

</details>

🚀🔑 `OBJECT_DIR` is powerful. Drop a new YAML file in `data/trees/` and it automatically becomes a new entry in the map. No config changes needed.


## 9. Generate pages from data

Now let's auto-generate a page for each tree. Configure the collection in `config/application.properties`:

```properties
site.collections.trees.layout=linktree
site.collections.trees.from-data.id-key=_key
site.collections.trees.link=/trees/:name
```

This tells Roq: "create a `trees` collection, generate one page per data entry, use the `linktree` layout, and use the YAML file name as the page ID."

**››› CODING TIME**

Create `templates/layouts/linktree.html` that extends `default` and renders a tree page. Show the tree's title and description at the top, then its links. Below a chevron separator, show the profile and append the profile's main links.

<details>
<summary>See hint</summary>

Use `page.data.title`, `page.data.description`, and `{#for link in page.data.links}` for the tree's own data. Reuse the profile partial and linkCard tag. The `show-profile` frontmatter key lets tree YAML files hide the profile section (defaults to `true`). The `profile-links` key controls whether profile links are appended.

</details>

<details>
<summary>See solution</summary>

Create `templates/layouts/linktree.html`:

```html
---
layout: default
robots: noindex
sitemap: false
---
{@io.quarkiverse.roq.frontmatter.runtime.model.Page page}
{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}

<div class="lt-page-relative">
  <a href="/trees" class="lt-nav-trees" title="All trees">
    <i class="ph ph-tree-structure" style="font-size: 24px;"></i>
  </a>
  <div class="lt-container">

    <div class="lt-heading">
      <h1 class="lt-heading-title">{page.title}</h1>
      {#if page.description}
      <p class="lt-title">{page.description}</p>
      {/if}
    </div>

    <div class="lt-links">
      {#for link in page.data.links}
      {#linkCard link=link /}
      {/for}
    </div>

    {#if page.data('show-profile', true)}
    <div class="lt-separator">
      <i class="ph ph-caret-double-down" style="font-size: 20px;"></i>
    </div>

    <div class="lt-profile-links">
      {#include partials/profile /}
    </div>

    {#if page.data('profile-links', true) and cdi:profile.links??}
    <div class="lt-links">
      {#for link in cdi:profile.links}
      {#linkCard link=link /}
      {/for}
    </div>
    {/if}
    {/if}

  </div>
</div>
```

</details>

🚀 Navigate to [http://localhost:8080/trees/guardians/](http://localhost:8080/trees/guardians/). You should see the Guardians tree with its links at the top, then a chevron separator, your profile, and your main links below. The tree icon in the top-right links to the gallery page (we'll build that next).

🚀🔑 This is the key insight: drop a new YAML file in `data/trees/` and Roq generates a new page automatically. The profile and main links appear at the bottom, giving visitors a path back to your other content. Add `show-profile: false` in a tree's YAML to hide the profile section on that specific page.


## 10. Add QR codes

Let's build a gallery page that lists all your link-trees with downloadable QR codes. This is great for sharing at events or printing on business cards.

First, add the QR code plugin:

```shell
roq add plugin:qrcode
```

**››› CODING TIME**

Create `templates/layouts/linktrees.html` that extends `default`, shows the profile, a link back home, and loops through all trees to display each as a card with a QR code.

<details>
<summary>See hint</summary>

Use `{#for tree in site.collections.trees}` to loop through the collection. The QR code tag is `{#qrcode value=tree.url.absolute alt=tree.data.title foreground="#0e4a5c" background="#FFFFFF" width=200 height=200 /}`. Access tree data with `tree.data.title` and `tree.data.description`. Link to the tree page with `tree.url`.

</details>

<details>
<summary>See solution</summary>

Create `templates/layouts/linktrees.html`:

```html
---
layout: default
---
{@io.quarkiverse.roq.frontmatter.runtime.model.Page page}
{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}

<div class="lt-page">
  <div class="lt-container">

    {#include partials/profile /}

    <a href="/" class="lt-home-link"><i class="ph ph-house" style="font-size: 16px;"></i> Profile</a>

    <div class="lt-heading">
      <h1 class="lt-heading-title">{page.title}</h1>
    </div>

    <div class="lt-trees">
      {#for tree in site.collections.trees}
      <div class="lt-tree-card">
        <h2 class="lt-tree-title">{tree.data.title}</h2>
        <p class="lt-tree-desc">{tree.data.description}</p>

        <div class="lt-qr-wrap qr-wrap" data-filename="qr-{tree.data.title.slugify}.svg">
          {#qrcode value=tree.url.absolute alt=tree.data.title foreground="#0e4a5c" background="#FFFFFF" width=200 height=200 /}
        </div>

        <div class="lt-tree-actions">
          <a href="{tree.url}" class="lt-tree-action">Open</a>
          <button onclick="downloadQR(this)" class="lt-tree-action-btn">Download QR</button>
        </div>
      </div>
      {/for}
    </div>

    {#insert /}

  </div>
</div>
```

</details>

Now add the QR download script. Create `web/scripts.js`:

```javascript
window.downloadQR = function(btn) {
  var wrap = btn.closest('.lt-tree-card').querySelector('.qr-wrap');
  var img = wrap.querySelector('img');
  if (!img) return;
  var filename = (wrap.dataset.filename || 'qr-code.svg').toLowerCase();
  var svgText = atob(img.src.split(',')[1]);
  var doc = new DOMParser().parseFromString(svgText, 'image/svg+xml');
  var svg = doc.querySelector('svg');
  svg.setAttribute('viewBox', '0 0 ' + svg.getAttribute('width') + ' ' + svg.getAttribute('height'));
  svg.setAttribute('width', '400');
  svg.setAttribute('height', '400');
  var blob = new Blob([new XMLSerializer().serializeToString(svg)], { type: 'image/svg+xml' });
  var a = document.createElement('a');
  a.download = filename;
  a.href = URL.createObjectURL(blob);
  a.click();
  URL.revokeObjectURL(a.href);
};
```

Finally, create the gallery content page. Create `content/trees.html`:

```html
---
layout: linktrees
title: All Trees
---
```

🚀 Navigate to [http://localhost:8080/trees](http://localhost:8080/trees). You should see your profile at the top, a "Profile" link back to the home page, and each tree displayed as a card with a QR code. Click "Download QR" to save it as an SVG.

🤩 Your link-tree site is complete! A profile card, social icons, link cards with hover effects, auto-generated pages from YAML data, and a QR code gallery. All styled with `@apply` CSS, all driven by data, all built with reusable layouts and components.


## What's next?

**Next in the series:** [Add Comments with Hybrid Mode](/posts/add-comments-hybrid/) to add dynamic features to your Roq blog with a database and server-rendered templates.

Here are a few ideas to keep going:

- **Add more trees**: drop a new YAML file in `data/trees/` (e.g. `work-links.yml`) and it's instantly available with its own QR code.
- **Deploy to GitHub Pages**: your project already includes a `.github/workflows/deploy.yml`. Push to GitHub, enable Pages in Settings, and you're live.
- **Add analytics**: set `analytics.ga4: G-XXXXX` in your site index frontmatter and add `{#ga4 /}` to the layout.
- **Make it your own**: swap the color palette (try `indigo`/`rose`), change the card style, add animations. It's just Tailwind, go wild.
- **Explore the docs**: [Roq the basics](/docs/basics/) covers collections, custom data, templates, and much more.

## Switch to the Linktree theme

Now that you understand how everything works under the hood, you can switch to the pre-built [Linktree theme](/theme/linktree-theme/) which provides all the layouts, partials, tags, and styles you just built. Run:

```shell
roq add theme:linktree
```

Then delete the files that the theme now provides: `templates/`, `web/app.css`, and `web/scripts.js`. Your data files (`data/profile.yml`, `data/trees/`) and content files (`content/index.html`, `content/trees.html`) stay the same, and you can customize the theme through `web/_custom.css`.

Happy linking!
