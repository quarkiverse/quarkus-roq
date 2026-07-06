---
title: "Create your own Blog with Roq (30min)"
slug: create-a-blog-with-roq
description: "Step-by-step tutorial: create and customize a blog with Roq using the default theme."
author: ia3andy
aliases: [lab]
tags: [tutorial]
series: roq-blog-lab
date: 2026-07-05 10:00
image: https://images.unsplash.com/photo-1461344577544-4e5dc9487184?q=80&w=1200&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
qute: false
---

So you want a blog. Not a WordPress behemoth, not a JavaScript-heavy framework that needs a PhD to configure. Just a clean, fast, good-looking blog that you can write in Markdown and deploy anywhere.

That's exactly what [Roq](https://iamroq.dev) is for. It's a static site generator powered by [Quarkus](https://quarkus.io), with blazing-fast live-reload, a beautiful default theme, and zero configuration to get started.

In about 30 minutes, you'll go from nothing to a fully customized blog deployed on GitHub Pages. Let's go!

> [!NOTE]
> **Prerequisites:** Install the Roq CLI by following the [Getting Started guide](/docs/getting-started/). Roq uses JBang, so no JDK installation is needed.
> Verify your setup with:
> ```
> roq --version
> ```

> [!TIP]
> For the best development experience, install the [Quarkus IDE tooling](https://quarkus.io/guides/ide-tooling) for your editor (VS Code, IntelliJ, or Eclipse). You get config autocompletion, validation, and Qute template completion.


## 1. Create your blog

Open a terminal and run:

```shell
roq create my-blog
```

This scaffolds a complete blog project with the default theme, example content, and everything you need.

Now start it:

```shell
cd my-blog
roq start
```

🚀 Hit `w` or open [http://localhost:8080](http://localhost:8080) in your browser. You should see a hero page with a mascot, feature cards, and a sidebar with navigation. Not bad for two commands!

> [!NOTE]
> `roq start` starts Quarkus in dev mode with **live-reload**. Every change you save is instantly reflected in the browser, no manual refresh needed.


## 2. 👀 Explore the generated structure

Your project uses the [default theme](https://github.com/quarkiverse/quarkus-roq/tree/main/roq-theme/default/runtime/src/main/resources), a Maven dependency that provides layouts, partials, styles, and much more. You don't need to look at the theme source to use it, but it helps to know it's there.

Before changing anything, take a minute to look at what was generated:

```
my-blog/
├── content/                    # Your pages and blog posts
│   ├── index.html              # Home page (also holds site-wide data)
│   ├── blog.html               # Blog listing page
│   ├── about.md                # About page
│   ├── 404.html                # Error page
│   └── posts/                  # Blog post collection
│       └── 2024-10-13-the-first-roq/
│           ├── index.md        # Post content
│           └── blog.avif       # Post image
├── data/                       # Data files (YAML/JSON)
│   ├── menu.yml                # Navigation menu
│   └── authors.yml             # Author profiles
├── public/                     # Static assets (images, favicon…)
│   └── images/
│       ├── logo.svg
│       └── mascot.svg
├── web/                        # CSS and JS (bundled automatically)
│   └── _custom.css             # Your color overrides
├── pom.xml                     # Maven build file
└── .github/workflows/
    └── deploy.yml              # GitHub Pages deployment (ready to go!)
```

Here's the mental model:
- **`content/`** is where you write. Markdown, AsciiDoc, or HTML. Pages at the root, posts in `posts/`.
- **`data/`** feeds the sidebar and templates. The menu, authors, and any structured data you want.
- **`web/`** is for styling. The theme provides the base CSS; `_custom.css` is your override layer.
- **`public/`** holds static files served as-is (images, fonts, robots.txt).
- **`templates/`** doesn't exist yet, but this is where you can create your own [layouts](/docs/basics/#layouts), [partials](/docs/basics/#partials), and [tags](/docs/basics/#tags) to override the theme.

🚀🔑 This is one of the key things to remember: content goes in `content/`, looks go in `web/`, data goes in `data/`.


## 3. Make it your own

The sidebar displays your site name, description, logo, and social links. All of this comes from the frontmatter in `content/index.html`, which acts as the site-wide data source.

**››› CODING TIME**

Open `content/index.html` and personalize the sidebar:

1. Change `name` to your blog's name
2. Update `description` with a short tagline about you or your blog
3. Find a nice avatar image (or generate one with AI!), save it as `public/images/avatar.png`, and update the `logo` field
4. Update the `social-*` fields with your own accounts (or remove the ones you don't use)

Also open `data/authors.yml` and update the default author with your own info: name, avatar, bio, and links.

<details>
<summary>See hint</summary>

The key frontmatter fields for the sidebar are: `name` (displayed as site name), `description` (shown below the name), `logo` (sidebar image, references a file in `public/images/`), and `social-twitter`, `social-github`, `social-linkedin` (contact icons).

For the avatar, try generating one with an AI image tool, or grab a photo and drop it in `public/images/`.

</details>

<details>
<summary>See solution</summary>

Edit the frontmatter in `content/index.html`:

```yaml
---
title: Jane's Dev Blog — Thoughts on code, coffee, and building things that work.
description: Software developer, open source enthusiast, and occasional writer.
name: Jane's Dev Blog
simple-name: Jane's Blog
image: avatar.png
logo: avatar.png
social-twitter: janecodes
social-github: janecodes
social-linkedin: janecodes
layout: home
---
```

Edit `data/authors.yml`:

```yaml
jane:
  name: Jane Doe
  nickname: janecodes
  job: Software Developer
  avatar: https://i.pravatar.cc/300
  profile: https://github.com/janecodes
  bio: Software developer who loves Java, open source, and writing about what I learn.
```

Drop your avatar image in `public/images/avatar.png`.

</details>

🚀 The sidebar should now show your name, your description, your avatar, and your social links. Looking good!

> [!NOTE]
> The `title` field in `content/index.html` is used for SEO (page title, Open Graph, etc.), while `name` is what appears in the sidebar. You'll typically want `title` to be a full sentence and `name` to be short.


## 4. Customize the navigation menu

The sidebar menu on the left comes from `data/menu.yml`. Right now it has two entries: Blog and About.

**››› CODING TIME**

Open `data/menu.yml` and add a "Projects" link that points to an external URL.

<details>
<summary>See hint</summary>

Each menu item has `title`, `path`, and `icon`. For external links, use a full URL. Icons use [Font Awesome](https://fontawesome.com/icons) classes. Add `target: _blank` for links that should open in a new tab.

</details>

<details>
<summary>See solution</summary>

Edit `data/menu.yml`:

```yaml
items:
  - title: Blog
    path: /blog
    icon: fa-solid fa-newspaper
  - title: Projects
    path: https://github.com/your-username
    icon: fa-solid fa-code
    target: _blank
  - title: About
    path: /about
    icon: fa-solid fa-user
```

</details>

🚀 Check the sidebar in your browser. The new links should appear instantly thanks to live-reload.


## 5. Edit the home page content

The body of `content/index.html` defines what visitors see when they land on your site. Right now it has the default Roq hero with a mascot. Let's make it yours.

**››› CODING TIME**

Open `content/index.html` and customize the hero section. Change the title, tagline, subtitle, and buttons to reflect your blog's personality.

<details>
<summary>See hint</summary>

The hero uses the `{#roq/hero}` tag with nested sections: `{#title}`, `{#tagline}`, `{#subtitle}`, and `<a>` buttons with `btn btn-primary` or `btn btn-secondary` classes. You can remove the `logo=` attribute if you don't want the mascot image.

</details>

<details>
<summary>See solution</summary>

Replace the body of `content/index.html` (everything between `---` and the end):

```html
{#roq/hero}
  {#title}Welcome to my <span class="shimmer">blog</span>!{/title}
  {#tagline}Code, coffee, and curiosity{/tagline}
  {#subtitle}I write about software development, open source projects, and things I learn along the way. Glad you're here.{/subtitle}
  <a href="/blog" class="btn btn-primary">Read the blog <i class="fa-solid fa-arrow-right"></i></a>
  <a href="/about" class="btn btn-secondary"><i class="fa-solid fa-user"></i> About me</a>
{/}

<div class="roq-features">
  {#roq/featureCard icon="fa-solid fa-pencil" title="Fresh Articles"}
    Regular posts about Java, Quarkus, and web development. Short, practical, and straight to the point.
  {/}
  {#roq/featureCard icon="fa-brands fa-github" title="Open Source"}
    I contribute to open source and share what I build. Check out my projects on GitHub.
  {/}
  {#roq/featureCard icon="fa-solid fa-mug-hot" title="Coffee Chats"}
    Sometimes I write about life, productivity, and the things that keep me going as a developer.
  {/}
</div>
```

</details>

🚀 Refresh your browser and admire your personalized home page!


## 6. Change the colors

The default theme ships with warm brown accent colors and sky blue "pop" colors (used for buttons, links, and gradients). All of this is controlled by CSS custom properties in `web/_custom.css`.

**››› CODING TIME**

Open `web/_custom.css` and change the accent color to `indigo` (a modern purple-blue) and the pop color to `emerald` (a fresh green for energy elements like buttons and links).

<details>
<summary>See hint</summary>

The theme includes all [Tailwind CSS colors](https://tailwindcss.com/docs/colors) as built-in variables: `var(--color-indigo-500)`, `var(--color-emerald-300)`, etc. Replace the hex values in `_custom.css` with these variable references for each shade (50 through 950). Add a `--color-pop-*` block for the pop color.

</details>

<details>
<summary>See solution</summary>

Replace the content of `web/_custom.css`:

```css
/* Theme customization: /theme/default/#css-customization */
@theme inline {
    /* Accent: indigo (sidebar, headings, borders) */
    --color-accent-50: var(--color-indigo-50);
    --color-accent-100: var(--color-indigo-100);
    --color-accent-200: var(--color-indigo-200);
    --color-accent-300: var(--color-indigo-300);
    --color-accent-400: var(--color-indigo-400);
    --color-accent-500: var(--color-indigo-500);
    --color-accent-600: var(--color-indigo-600);
    --color-accent-700: var(--color-indigo-700);
    --color-accent-800: var(--color-indigo-800);
    --color-accent-900: var(--color-indigo-900);
    --color-accent-950: var(--color-indigo-950);

    /* Pop: emerald (buttons, links, shimmer) */
    --color-pop-50: var(--color-emerald-50);
    --color-pop-100: var(--color-emerald-100);
    --color-pop-200: var(--color-emerald-200);
    --color-pop-300: var(--color-emerald-300);
    --color-pop-400: var(--color-emerald-400);
    --color-pop-500: var(--color-emerald-500);
    --color-pop-600: var(--color-emerald-600);
    --color-pop-700: var(--color-emerald-700);
    --color-pop-800: var(--color-emerald-800);
    --color-pop-900: var(--color-emerald-900);
    --color-pop-950: var(--color-emerald-950);
}
```

</details>

🚀 Your site should now have a completely different vibe! Try a few other color combos: `rose` + `amber`, `cyan` + `orange`, `violet` + `lime`... go wild.

Now click the **moon icon** in the top-right corner to toggle dark mode. The theme handles both modes automatically.

🤩 Your blog already looks nothing like the default. And you haven't written a single line of Java.


## 7. Write your first blog post

Time to actually write something. You have two ways to create a post.

### Option A: The Roq Editor (recommended)

In the terminal where `roq start` is running, press **`m`** (for *Manage*). This opens the Roq Editor in your browser, a rich-text editor with Markdown support, right inside the dev experience.

From the editor, click **New Post**, give it a title, write your content, and save. The file is created for you in `content/posts/`.

### Option B: Create the file manually

Create a new directory in `content/posts/` following the naming pattern `YYYY-MM-DD-slug/`:

```shell
mkdir -p content/posts/2026-07-05-my-first-post
```

**››› CODING TIME**

Create `content/posts/2026-07-05-my-first-post/index.md` with YAML frontmatter and some Markdown content. Include at least a title, description, tags, and author.

<details>
<summary>See hint</summary>

Every post starts with YAML frontmatter between `---` markers. The key fields are:
- `title` (string): the post title
- `description` (string): shown in previews and SEO
- `tags` (comma-separated): used for categorization
- `date` (YYYY-MM-DD or full datetime): publication date
- `author` (string): matches a key in `data/authors.yml`
- `image` (string): header image (URL or local file in the same directory)

</details>

<details>
<summary>See solution</summary>

Create `content/posts/2026-07-05-my-first-post/index.md`:

```markdown
---
title: "My First Blog Post"
description: "Hello world! This is my very first post on my brand new blog."
tags: hello, blogging
date: 2026-07-05
author: jane
---

## Hello, World!

Welcome to my blog. I built this site with [Roq](https://iamroq.dev), a static site generator powered by Quarkus.

It took me about 30 minutes to go from `roq create` to a fully customized blog. Here's what I like about it so far:

- **Live-reload** makes writing a joy. Save the file, see it instantly.
- **Markdown** keeps things simple. No complex editors, just text.
- **The default theme** looks professional out of the box.

I'll be writing about code, open source, and whatever else catches my attention. Stay tuned!
```

</details>

🚀 Head to [http://localhost:8080/blog](http://localhost:8080/blog). Your new post should appear in the listing!

🚀🔑 Click on it. Notice how the theme automatically renders the reading time, the date, the author info (pulled from `data/authors.yml`), the tags, and even social sharing buttons. All from a simple Markdown file with a few frontmatter fields.

> [!NOTE]
> You can also add an image to your post. Drop an image file (e.g. `cover.jpg`) in the same directory as your `index.md` and add `image: cover.jpg` to the frontmatter. It becomes the post header image and the social media preview.


## 8. Power up with plugins

Roq has a plugin system. Each plugin is a Quarkus extension you can add with a single command. Let's add three useful ones.

### Tagging

If your posts use `tags` in frontmatter (yours already does!), the tagging plugin auto-generates tag pages so visitors can browse posts by topic.

```shell
roq add plugin:tagging
```

That's it. The default theme already includes a tag layout, so tag pages are immediately available.

🚀 Click on a tag in your blog post. You should land on a page listing all posts with that tag, for example `/posts/tag/blogging`.

### Faker (development helper)

Writing a blog with just one post makes it hard to see how pagination and layouts work at scale. The faker plugin generates realistic fake posts during development.

```shell
roq add plugin:faker
```

Then add this to `config/application.properties` (or `src/main/resources/application.properties`):

```properties
quarkus.roq.faker.count.posts=20
```

🚀 Refresh your blog listing. You should now have 20+ posts with random titles, images, tags, and lorem ipsum content. The fake posts only exist in dev mode and are never published.

🤩 Scroll through your blog. With pagination, tags, and a pile of content, your blog is starting to feel like a real site!

### Search (optional)

For full-text search powered by [Lunr.js](https://lunrjs.com/), add the search plugin:

```shell
roq add plugin:lunr
```

**››› CODING TIME**

Override the theme's `main.html` layout to add the search overlay and button.

<details>
<summary>See hint</summary>

You need two things: a `content/search-index.json` file that generates the search index, and a `templates/layouts/main.html` override with three Qute tags: `{#search-overlay /}` for the modal, `{#search-button-input /}` for the trigger in the sidebar, and `{#search-script /}` in the head. See the [Lunr Search plugin docs](/plugin/lunr-search/) for details.

</details>

<details>
<summary>See solution</summary>

Create `content/search-index.json`:

```
{#include fm/search-index.json /}
```

Create or edit `templates/layouts/main.html`:

```html
---
theme-layout: main
---

{#head}{#search-script /}{/head}

{#search-overlay /}
{#insert /}

{#menu}
{#search-button-input /}
{#include partials/roq-default/sidebar-menu menu=cdi:menu.items /}
{/}
```

</details>

🚀 Refresh your blog and press **Cmd+K** (or **Ctrl+K**). A search overlay should appear, letting you search across all your posts.


## 9. Deploy to GitHub Pages

Your blog already includes a `.github/workflows/deploy.yml` file that handles everything. All you need is a GitHub repository.

**››› CODING TIME**

Push your blog to GitHub and enable GitHub Pages.

<details>
<summary>See hint</summary>

1. Create a new repository on GitHub (e.g. `my-blog`)
2. Push your code using `git init`, `git add`, `git commit`, and `git push`
3. In the repository settings, go to **Pages** and set the source to **GitHub Actions**

</details>

<details>
<summary>See solution</summary>

```shell
git init
git add .
git commit -m "Initial blog"
gh repo create my-blog --public --source=. --push
```

Then go to your repository on GitHub:
1. Click **Settings** > **Pages**
2. Under "Build and deployment", set Source to **GitHub Actions**

The workflow triggers automatically on push to `main`. It also runs daily at 5:00 UTC to publish any scheduled content (posts with a future `date`).

Your blog will be live at `https://your-username.github.io/my-blog/` within a couple of minutes.

</details>

🚀 The first run will likely fail because GitHub Pages is not yet enabled. Go to **Settings** > **Pages**, set Source to **GitHub Actions**, then re-run the workflow from the **Actions** tab. Once it passes, visit your live URL.

🤩 Your blog is live on the internet. You built it from scratch, customized the theme, wrote your first post, and deployed it. All in about 30 minutes.


## What's next?

**Next in the series:** [Create a Link-Tree with Roq](/posts/create-a-link-tree-with-roq/) or go deeper with [Create a Blog from Scratch](/posts/create-a-blog-from-scratch-with-roq/) to learn how layouts, collections, and templates work under the hood.

Here are a few ideas to keep going:

- **Write more posts!** Just create new directories in `content/posts/` or use the Editor (`m`).
- **Add an [RSS feed](/docs/basics/#rss)**: create `content/rss.xml` with `{#include fm/rss.html /}` inside.
- **Add a sitemap**: `roq add plugin:sitemap` and it's done.
- **Override theme templates**: create `templates/layouts/post.html` to customize the post layout. The theme is your base, not your cage.
- **Explore the docs**: [Roq the basics](/docs/basics/) covers collections, custom data, templates, variables, and much more.

Happy blogging!
