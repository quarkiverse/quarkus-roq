---
title: "Create a Link-Tree with Roq (45min)"
description: "Step-by-step tutorial: build a personal link-tree site from scratch with Roq."
author: ia3andy
qute: false
tags: [tutorial]
date: 2026-07-07
image: https://images.unsplash.com/photo-1528183429752-a97d0bf99b5a?q=80&w=1200&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
---

Link-tree sites are everywhere: a clean page with your photo, a few links, maybe some social icons. Simple enough that you could build one in plain HTML, but what if you want multiple link pages, QR codes, and zero-effort deployment?

In this tutorial you'll build a personal link-tree from scratch with [Roq](https://iamroq.dev) and [Tailwind CSS](https://tailwindcss.com). No pre-built theme, just you, a data file, and some templates. By the end you'll have a polished site with typed data, icon sets, auto-generated pages, and downloadable QR codes.

> [!NOTE]
> **Prerequisites:** Install the Roq CLI by following the [Getting Started guide](https://iamroq.dev/docs/getting-started/). Roq uses JBang, so no JDK installation is needed.
> Verify your setup with:
> ```
> roq --version
> ```

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
roq add web-bundler-tailwindcss
```

Start dev mode:

```shell
roq dev
```

🚀 Open [http://localhost:8080](http://localhost:8080). You should see a bare-bones page with no styling. That's expected, we'll fix that next.


## 2. Create the base layout

The base theme provides a minimal `default` layout, but we need our own with Tailwind classes for colors and dark mode.

**››› CODING TIME**

Create `templates/layouts/default.html` with a basic HTML skeleton. Use Tailwind's `slate` palette for a dark/light look, import the page title, and include the `{#bundle /}` tag in the head to pull in CSS and JS.

<details>
<summary>See hint</summary>

Roq layouts use Qute templates. You need two type declarations at the top: `{@io.quarkiverse.roq.frontmatter.runtime.model.Page page}` and `{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}`. Use `{page.title}` for the `<title>` tag. The `{#bundle /}` tag auto-includes all CSS/JS from the `web/` directory. `{#insert /}` is where page content gets injected.

</details>

<details>
<summary>See solution</summary>

Create `templates/layouts/default.html`:

```html
{@io.quarkiverse.roq.frontmatter.runtime.model.Page page}
{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}
<!DOCTYPE html>
<html lang="en" class="bg-slate-100 dark:bg-slate-900">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>{page.title}</title>
  {#bundle /}
</head>
<body class="bg-slate-100 dark:bg-slate-900 text-slate-800 dark:text-slate-200 font-sans overscroll-none min-h-screen">
  {#insert /}
</body>
</html>
```

</details>

Now set up the CSS. Replace the contents of `web/app.css` with a Tailwind import:

```css
@import "tailwindcss";
```

🚀 Refresh your browser. The page should now have a light gray background (slate-100) and sans-serif text. Dark mode works automatically based on your system preference.

🚀🔑 The `{#bundle /}` tag is the magic glue. It tells Roq's Web Bundler to compile everything in `web/` (CSS, JS, npm packages) and inject it into the page. Tailwind is processed at build time, so only the classes you actually use end up in the final CSS.


## 3. Create the profile data

Link-tree sites are all about *you*. Let's define your profile as structured data that templates can access.

**››› CODING TIME**

Create `data/profile.yml` with your name, handle, title, bio, profile image, and a list of social links. Each social entry should have a `name`, `url`, and `icon`.

<details>
<summary>See hint</summary>

YAML files in `data/` are automatically available in templates via `cdi:` prefix. For the icon field, we'll use [Phosphor Icons](https://phosphoricons.com/) names later. For now, just pick names like `github-logo`, `linkedin-logo`, `butterfly` (for Bluesky).

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
tree: my-links
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
```

Drop a profile image as `public/images/groot.png` (or any image you like).

</details>

The `tree: my-links` field will come in handy later to specify which link-tree to show on the home page.


## 4. Map the profile data to Java

Raw YAML data works in templates, but Roq can do better. With `@DataMapping`, you map your YAML to a typed Java record. This gives you compile-time safety and auto-completion.

**››› CODING TIME**

Create `src/main/java/io/acme/Profile.java` as a Java record annotated with `@DataMapping("profile")`. Include fields for `name`, `handle`, `title`, `bio`, `image`, `tree`, and a `List<Social>` where `Social` is a nested record with `name`, `url`, `icon`.

<details>
<summary>See hint</summary>

`@DataMapping("profile")` tells Roq to map `data/profile.yml` to this record. The field names must match the YAML keys. For nested lists, use a nested record type. Import from `io.quarkiverse.roq.data.runtime.annotations.DataMapping`.

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
                       String image, String tree, List<Social> social) {

    public record Social(String name, String url, String icon) {}
}
```

</details>

🚀🔑 The `@DataMapping` annotation is the bridge between your YAML data and your templates. Once mapped, you access the profile in templates with `cdi:profile.name`, `cdi:profile.handle`, etc. The `cdi:` prefix means it's a CDI bean, which is how Quarkus manages dependency injection.


## 5. Build the home page

Time to turn that blank page into a link-tree. The home page will show your profile (avatar, name, handle, title) and your default set of links.

**››› CODING TIME**

Edit `content/index.html` to display the profile card: avatar image, name, handle, and title. Use Tailwind classes for centering, spacing, and the avatar circle. For now, skip the links, we'll add those next.

<details>
<summary>See hint</summary>

Use `cdi:profile` to access your data. For the avatar, Roq provides `site.image()` to resolve images from `public/images/`. So `{site.image(cdi:profile.image)}` gives you the correct URL. Wrap the profile in a centered container with `max-w-md mx-auto`. Use `{#if}` to handle optional fields gracefully.

</details>

<details>
<summary>See solution</summary>

Replace the content of `content/index.html`:

```html
---
layout: default
title: Groot
---

<div class="flex items-start justify-center pb-6 pt-12">
  <div class="w-full max-w-md mx-auto space-y-8 px-4">

    <!-- Profile -->
    <div class="text-center space-y-3">
      {#if cdi:profile.image}
      <img src="{site.image(cdi:profile.image)}" alt="{cdi:profile.name}" class="w-32 h-32 mx-auto rounded-full bg-white dark:bg-slate-800 p-2 shadow-md">
      {/if}
      <h1 class="text-2xl font-bold tracking-tight">
        {#if cdi:profile.name}<span class="text-slate-900 dark:text-white">{cdi:profile.name}</span>{/if}
        {#if cdi:profile.handle}<span class="text-sky-500 dark:text-sky-400">{#if cdi:profile.name} {/if}{cdi:profile.handle}</span>{/if}
      </h1>
      <p class="text-sm text-slate-500 dark:text-slate-400">{cdi:profile.title}</p>
    </div>

  </div>
</div>
```

</details>

🚀 Refresh your browser. You should see your avatar in a circle, your name in dark text, your handle in sky blue, and your title below. Clean and centered.


## 6. Add social icons

Those social links in your profile need icons. We'll use [Phosphor Icons](https://phosphoricons.com/), a flexible icon family available as an npm package through [mvnpm](https://mvnpm.org).

First, add the Phosphor Icons dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.mvnpm.at.phosphor-icons</groupId>
    <artifactId>web</artifactId>
    <version>2.1.2</version>
    <scope>provided</scope>
</dependency>
```

Then import the icon styles in `web/app.css`:

```css
@import "tailwindcss";
@import "@phosphor-icons/web/regular";
@import "@phosphor-icons/web/fill";
```

**››› CODING TIME**

Now add a social icons row below the profile in `content/index.html`. Loop through `cdi:profile.social` and render each as an icon link.

<details>
<summary>See hint</summary>

Phosphor icon classes follow the pattern `ph ph-{icon-name}` for regular and `ph-fill ph-{icon-name}` for filled variants. Use `style="font-size: 24px;"` to size them. Loop with `{#for s in cdi:profile.social}`.

</details>

<details>
<summary>See solution</summary>

Add this block after the profile `</div>` and before the closing `</div>` tags in `content/index.html`:

```html
    <!-- Social -->
    <div class="text-center space-y-3">
      <div class="flex items-center justify-center gap-5">
        {#for s in cdi:profile.social}
        <a href="{s.url}" target="_blank" rel="noopener noreferrer" aria-label="{s.name}"
           class="text-slate-500 dark:text-slate-400 hover:text-sky-600 dark:hover:text-sky-400 transition-colors">
          <i class="ph-fill ph-{s.icon}" style="font-size: 24px;"></i>
        </a>
        {/for}
      </div>
    </div>
```

</details>

🚀 You should now see GitHub, LinkedIn, and Bluesky icons below your name. Hover over them to see the sky-blue highlight.

🤩 The icons come from an npm package, managed through Maven, bundled automatically. No CDN, no manual downloads.


## 7. Create the links data

Now for the main event: the links. We'll store them in a YAML file inside a `data/trees/` directory. This structure lets you have multiple link-trees later.

**››› CODING TIME**

Create `data/trees/my-links.yml` with a title, description, and a list of links. Each link should have a `name`, `url`, `description`, and `icon` (Phosphor icon name).

<details>
<summary>See hint</summary>

Pick any links you want. For icons, browse [phosphoricons.com](https://phosphoricons.com/) and use the icon name (e.g. `lightning`, `terminal`, `tree`, `shield-star`). The file name `my-links` matches the `tree: my-links` value in your profile data.

</details>

<details>
<summary>See solution</summary>

Create `data/trees/my-links.yml`:

```yaml
title: Groot's Links
description: I am Groot's link tree
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

</details>


## 8. Map the trees data to Java

Just like the profile, we'll map the trees data to a typed Java record. The difference is that trees live in a *directory* (`data/trees/`), so we use `DataMapping.Type.OBJECT_DIR` to load all files in that directory as a map.

**››› CODING TIME**

Create `src/main/java/io/acme/Trees.java` with `@DataMapping(value = "trees", type = DataMapping.Type.OBJECT_DIR)`. The record should contain a `Map<String, Tree>` where each key is the file name (e.g. `my-links`) and `Tree` has `title`, `description`, and `List<Link>`.

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

🚀🔑 `OBJECT_DIR` is powerful. Drop a new YAML file in `data/trees/` and it automatically becomes a new entry in the map. No config changes needed. This is how we'll support multiple link-trees later.


## 9. Render the links

Now wire the links data into the home page. We'll fetch the default tree using the `tree` field from the profile, then loop through its links to render styled cards.

**››› CODING TIME**

Add a links section to `content/index.html`. Use `{#let}` to grab the default tree from `cdi:trees.map`, then `{#for}` to loop through its links. Each link should be a card with an icon, name, description, and an arrow.

<details>
<summary>See hint</summary>

Use `{#let defaultTree=cdi:trees.map.get(cdi:profile.tree)}` to fetch the tree matching your profile's `tree` field. Then `{#for link in defaultTree.links}` to iterate. For the card, use a `<a>` tag with Tailwind classes: `rounded-lg`, `border`, `bg-white`, `hover:border-sky-400`, `hover:shadow-md`, `transition-all`. Add a Phosphor `ph-arrow-right` icon at the end.

</details>

<details>
<summary>See solution</summary>

Wrap the entire `content/index.html` body in a `{#let}` block and add the links section. Here is the complete file:

```html
---
layout: default
title: Groot
---

{#let defaultTree=cdi:trees.map.get(cdi:profile.tree)}
<div class="flex items-start justify-center pb-6 pt-12 relative">
  <div class="w-full max-w-md mx-auto space-y-8 px-4">

    <!-- Profile -->
    <div class="text-center space-y-3">
      {#if cdi:profile.image}
      <img src="{site.image(cdi:profile.image)}" alt="{cdi:profile.name}" class="w-32 h-32 mx-auto rounded-full bg-white dark:bg-slate-800 p-2 shadow-md">
      {/if}
      <h1 class="text-2xl font-bold tracking-tight">
        {#if cdi:profile.name}<span class="text-slate-900 dark:text-white">{cdi:profile.name}</span>{/if}
        {#if cdi:profile.handle}<span class="text-sky-500 dark:text-sky-400">{#if cdi:profile.name} {/if}{cdi:profile.handle}</span>{/if}
      </h1>
      <p class="text-sm text-slate-500 dark:text-slate-400">{cdi:profile.title}</p>
    </div>

    <!-- Social -->
    <div class="text-center space-y-3">
      <div class="flex items-center justify-center gap-5">
        {#for s in cdi:profile.social}
        <a href="{s.url}" target="_blank" rel="noopener noreferrer" aria-label="{s.name}"
           class="text-slate-500 dark:text-slate-400 hover:text-sky-600 dark:hover:text-sky-400 transition-colors">
          <i class="ph-fill ph-{s.icon}" style="font-size: 24px;"></i>
        </a>
        {/for}
      </div>
    </div>

    <!-- Links -->
    <div class="space-y-3">
      {#for link in defaultTree.links}
      <a href="{link.url}" target="_blank" rel="noopener noreferrer"
         class="group flex items-center justify-between px-5 py-4 rounded-lg
                border border-slate-200 dark:border-slate-700
                bg-white dark:bg-slate-800
                hover:border-sky-400 dark:hover:border-sky-500
                hover:shadow-md
                transition-all duration-200">
        <span class="flex items-center gap-3">
          {#if link.icon}
          <i class="ph ph-{link.icon} text-sky-500 dark:text-sky-400" style="font-size: 20px;"></i>
          {/if}
          <span>
            <span class="text-sm font-medium text-slate-800 dark:text-slate-100">{link.name}</span>
            <span class="block text-xs text-slate-500 dark:text-slate-400">{link.description}</span>
          </span>
        </span>
        <span class="text-slate-300 dark:text-slate-600 group-hover:text-sky-500 dark:group-hover:text-sky-400 transition-colors">
          <i class="ph ph-arrow-right" style="font-size: 16px;"></i>
        </span>
      </a>
      {/for}
    </div>

  </div>
</div>
{/let}
```

</details>

🚀 Refresh. You should see your full link-tree: avatar, name, social icons, and a list of link cards with icons, hover effects, and arrows.

🤩 That's a complete link-tree, and it's all driven by YAML data. Want to change a link? Edit the YAML. Want to add one? Add a line. No template changes needed.


## 10. Generate pages from data

Right now you have one link-tree. But what if you want multiple? Roq can auto-generate a page for each YAML file in `data/trees/` using the `from-data` collection feature.

First, configure the collection in `config/application.properties`:

```properties
site.collections.trees.layout=tree
site.collections.trees.from-data.id-key=_key
```

This tells Roq: "create a `trees` collection, generate one page per data entry, use the `tree` layout, and use the YAML file name as the page ID."

**››› CODING TIME**

Create `templates/layouts/tree.html` that extends `default` and renders a tree page. It's very similar to the home page, but uses `page.data.links` instead of `defaultTree.links` since the links come directly from the page data.

<details>
<summary>See hint</summary>

Start with `layout: default` in the frontmatter to inherit the base layout. Copy the profile and social sections from `index.html`. For the links, use `{#for link in page.data.links}` since each tree YAML file becomes the page's data. The `page.data` object contains `title`, `description`, and `links` from the YAML.

</details>

<details>
<summary>See solution</summary>

Create `templates/layouts/tree.html`:

```html
---
layout: default
---
{@io.quarkiverse.roq.frontmatter.runtime.model.Page page}
{@io.quarkiverse.roq.frontmatter.runtime.model.Site site}

<div class="flex items-start justify-center pb-6 pt-12">
  <div class="w-full max-w-md mx-auto space-y-8 px-4">

    <!-- Profile -->
    <div class="text-center space-y-3">
      {#if cdi:profile.image}
      <img src="{site.image(cdi:profile.image)}" alt="{cdi:profile.name}" class="w-32 h-32 mx-auto rounded-full bg-white dark:bg-slate-800 p-2 shadow-md">
      {/if}
      <h1 class="text-2xl font-bold tracking-tight">
        {#if cdi:profile.name}<span class="text-slate-900 dark:text-white">{cdi:profile.name}</span>{/if}
        {#if cdi:profile.handle}<span class="text-sky-500 dark:text-sky-400">{#if cdi:profile.name} {/if}{cdi:profile.handle}</span>{/if}
      </h1>
      <p class="text-sm text-slate-500 dark:text-slate-400">{cdi:profile.title}</p>
    </div>

    <!-- Social -->
    <div class="text-center space-y-3">
      <div class="flex items-center justify-center gap-5">
        {#for s in cdi:profile.social}
        <a href="{s.url}" target="_blank" rel="noopener noreferrer" aria-label="{s.name}"
           class="text-slate-500 dark:text-slate-400 hover:text-sky-600 dark:hover:text-sky-400 transition-colors">
          <i class="ph-fill ph-{s.icon}" style="font-size: 24px;"></i>
        </a>
        {/for}
      </div>
    </div>

    <!-- Links -->
    <div class="space-y-3">
      {#for link in page.data.links}
      <a href="{link.url}" target="_blank" rel="noopener noreferrer"
         class="group flex items-center justify-between px-5 py-4 rounded-lg
                border border-slate-200 dark:border-slate-700
                bg-white dark:bg-slate-800
                hover:border-sky-400 dark:hover:border-sky-500
                hover:shadow-md
                transition-all duration-200">
        <span class="flex items-center gap-3">
          {#if link.icon}
          <i class="ph ph-{link.icon} text-sky-500 dark:text-sky-400" style="font-size: 20px;"></i>
          {/if}
          <span>
            <span class="text-sm font-medium text-slate-800 dark:text-slate-100">{link.name}</span>
            <span class="block text-xs text-slate-500 dark:text-slate-400">{link.description}</span>
          </span>
        </span>
        <span class="text-slate-300 dark:text-slate-600 group-hover:text-sky-500 dark:group-hover:text-sky-400 transition-colors">
          <i class="ph ph-arrow-right" style="font-size: 16px;"></i>
        </span>
      </a>
      {/for}
    </div>

  </div>
</div>
```

</details>

🚀 Navigate to [http://localhost:8080/my-links/](http://localhost:8080/my-links/). You should see the same link-tree as the home page, but this page was auto-generated from `data/trees/my-links.yml`.

🚀🔑 This is the key insight: drop a new YAML file in `data/trees/` (e.g. `work-links.yml`) and Roq generates a new page at `/work-links/` automatically. No new templates, no config changes.


## 11. Add QR codes

Let's build a "gallery" page that lists all your link-trees with downloadable QR codes. This is great for sharing at events or printing on business cards.

First, add the QR code plugin to your `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkiverse.roq</groupId>
    <artifactId>quarkus-roq-plugin-qrcode</artifactId>
</dependency>
```

**››› CODING TIME**

Create `content/trees.html` that loops through `site.collections.trees` and shows each tree as a card with its title, description, QR code, and a link to open it.

<details>
<summary>See hint</summary>

Use `{#for tree in site.collections.trees}` to loop through the collection. The QR code tag is `{#qrcode value=tree.url.absolute alt=tree.data.title foreground="#0e4a5c" background="#FFFFFF" width=200 height=200 /}`. Access tree data with `tree.data.title` and `tree.data.description`. Link to the tree page with `tree.url`.

</details>

<details>
<summary>See solution</summary>

Create `content/trees.html`:

```html
---
layout: default
title: All Trees
---

<div class="flex items-start justify-center pb-6 pt-12">
  <div class="w-full max-w-md mx-auto space-y-8 px-4">

    <div class="text-center space-y-2">
      <h1 class="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">Link Trees</h1>
      <p class="text-sm text-slate-500 dark:text-slate-400">{cdi:profile.name}</p>
    </div>

    <div class="space-y-6">
      {#for tree in site.collections.trees}
      <div class="text-center space-y-4 p-5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800">
        <h2 class="text-lg font-bold text-slate-800 dark:text-slate-100">{tree.data.title}</h2>
        <p class="text-xs text-slate-500 dark:text-slate-400">{tree.data.description}</p>

        <div class="inline-block p-3 bg-white rounded-xl qr-wrap" data-filename="qr-{tree.data.title.slugify}.png">
          {#qrcode value=tree.url.absolute alt=tree.data.title foreground="#0e4a5c" background="#FFFFFF" width=200 height=200 /}
        </div>

        <div class="flex items-center justify-center gap-4">
          <a href="{tree.url}" class="text-sm text-sky-600 dark:text-sky-400 hover:text-sky-800 dark:hover:text-sky-200 transition-colors">
            Open
          </a>
          <button onclick="downloadQR(this)" class="text-sm text-sky-600 dark:text-sky-400 hover:text-sky-800 dark:hover:text-sky-200 transition-colors cursor-pointer">
            Download QR
          </button>
        </div>
      </div>
      {/for}
    </div>

  </div>
</div>
```

</details>

Now add the QR download script. Create `web/scripts.js`:

```javascript
window.downloadQR = function(btn) {
  var wrap = btn.closest('.text-center').querySelector('.qr-wrap');
  var img = wrap.querySelector('img');
  if (!img) return;
  var filename = (wrap.dataset.filename || 'qr-code.png').toLowerCase();
  var canvas = document.createElement('canvas');
  canvas.width = 200;
  canvas.height = 200;
  var ctx = canvas.getContext('2d');
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, 200, 200);
  ctx.drawImage(img, 0, 0, 200, 200);
  var a = document.createElement('a');
  a.download = filename;
  a.href = canvas.toDataURL('image/png');
  a.click();
};
```

🚀 Navigate to [http://localhost:8080/trees](http://localhost:8080/trees). You should see your link-tree with a QR code. Click "Download QR" to save it as a PNG.


## 12. Add navigation

Let's add a small navigation icon on the home page to link to the trees gallery.

**››› CODING TIME**

Add a link to `/trees` in the top-right corner of `content/index.html`, using the Phosphor `tree-structure` icon.

<details>
<summary>See hint</summary>

Add an `<a>` tag with `class="absolute top-4 right-4"` inside the `relative` container div. Use `<i class="ph ph-tree-structure" style="font-size: 24px;"></i>` for the icon.

</details>

<details>
<summary>See solution</summary>

In `content/index.html`, add the `relative` class to the outer flex div (if not already there) and add this link right after it opens:

```html
  <a href="/trees" class="absolute top-4 right-4 text-slate-400 dark:text-slate-500 hover:text-sky-500 dark:hover:text-sky-400 transition-colors" title="All trees">
    <i class="ph ph-tree-structure" style="font-size: 24px;"></i>
  </a>
```

</details>

🚀 You should now see a small tree icon in the top-right corner of the home page that links to the gallery.

🤩 Your link-tree site is complete! A profile card, social icons, link cards with hover effects, auto-generated pages from YAML data, and a QR code gallery. All styled with Tailwind, all driven by data.


## What's next?

Here are a few ideas to keep going:

- **Add more trees**: drop a new YAML file in `data/trees/` (e.g. `work-links.yml`) and it's instantly available at `/work-links/` with its own QR code.
- **Deploy to GitHub Pages**: your project already includes a `.github/workflows/deploy.yml`. Push to GitHub, enable Pages in Settings, and you're live.
- **Add analytics**: set `analytics.ga4: G-XXXXX` in your site index frontmatter and add `{#ga4 /}` to the layout.
- **Make it your own**: swap the color palette (try `indigo`/`rose`), change the card style, add animations. It's just Tailwind, go wild.
- **Explore the docs**: [Roq the basics](https://iamroq.dev/docs/basics/) covers collections, custom data, templates, and much more.

Happy linking!
