---
title: Migrate a WordPress blog to Roq
slug: migrate-from-wordpress-to-roq
description: "Step-by-step tutorial: move the posts, pages, images, categories and permalinks of a WordPress site to Roq."
author: and-y
qute: false
tags:
  - tutorial
  - migration
  - wordpress
date: 2026-09-28 12:00
image: https://images.unsplash.com/photo-1580901369227-308f6f40bdeb?q=80&w=1200&auto=format&fit=crop
---
This tutorial moves the content of a WordPress site to Roq: posts, pages, images, drafts, categories, tags and the exact URLs WordPress published them under. It does not convert your WordPress theme or plugins. Roq comes with its own theme, and you rebuild the look you want with Qute templates.

Every step below was run against a WordPress 7.1.2 site loaded with the [WordPress Theme Unit Test data](https://github.com/WPTT/theme-unit-test), a public export that exists to stress themes with block editor posts, classic editor posts, galleries, captions, nested pages and edge cases like titles full of special characters. The converter found 59 posts and 22 pages and downloaded 102 images. After the steps in this tutorial, 78 of the 79 published WordPress URLs resolved to a page at the same path in the generated Roq site, and no image was broken. The one missing URL is the default WordPress "Sample Page", which the converter skips on purpose.

> [!NOTE]
> **Prerequisites:**
>
> - The Roq CLI, see the [Getting Started guide](/docs/getting-started/). Verify it with `roq --version`.
> - [Node.js](https://nodejs.org/), to run the WordPress to Markdown converter and a small fix-up script. Verify it with `node --version`.
> - Administrator access to your WordPress site, or an export file someone made for you.

## 1. Export your WordPress content

In the WordPress admin, go to **Tools → Export**, keep **All content** selected and click **Download Export File**. You get an XML file in the WordPress eXtended RSS (WXR) format with every post, page, attachment, category and tag.

Create a working folder and put the file there as `export.xml`:

```shell
mkdir wordpress-to-roq
cd wordpress-to-roq
mv ~/Downloads/yoursite.WordPress.2026-09-25.xml export.xml
```

Keep the WordPress site running until the migration is done: the converter downloads the images from it.

## 2. Convert the export to Markdown

[wordpress-export-to-markdown](https://github.com/lonekorean/wordpress-export-to-markdown) turns the export into one Markdown file per post and page, and saves the images next to them:

```shell
npx wordpress-export-to-markdown@3.0.6 --wizard=false \
  --input=export.xml --output=wordpress-content \
  --prefix-date=true \
  --frontmatter-fields=id,title,date,slug,categories,tags,coverImage,excerpt,draft \
  --date-format="yyyy-MM-dd HH:mm:ss ZZZ"
```

The options matter for Roq:

- `--prefix-date=true` names each folder `2018-11-03-block-image`, the naming Roq uses for posts.
- `--frontmatter-fields` adds `id` and `excerpt` to the defaults. The fix-up script in step 4 uses `id` to find the original URL of each post and turns `excerpt` into the Roq `description`.
- `--date-format` writes dates like `2018-11-03 15:20:00 +0000`, which matches the default Roq `site.date-format` (`yyyy-MM-dd[ HH:mm][:ss][ Z]`). With the converter default (`2018-11-03T15:20:00.000Z`), the Roq build fails with `Invalid date format`.

The result is a `wordpress-content/posts` and a `wordpress-content/pages` folder. Each post folder holds an `index.md` and an `images` folder:

```
wordpress-content/posts/2018-11-03-block-image/
├── images/
│   ├── image-alignment-150x150-1.jpg
│   └── ...
└── index.md
```

Block editor content converts well: paragraphs, headings, lists, quotes, tables, code and images with captions all come out as Markdown, or as a little HTML where Markdown has no equivalent, like `<figure>` with a `<figcaption>`.

## 3. Create the Roq site

Create a site with the default theme and the tagging plugin, which generates a page per tag:

```shell
roq create my-blog -x plugin:tagging
cd my-blog
```

The new site comes with starter content for a blank blog. Clean it up before you copy your WordPress content in, so none of it ends up on your site or collides with your pages:

```shell
rm -r content/posts/2024-10-13-the-first-roq
rm content/about.md
```

- `content/posts/2024-10-13-the-first-roq` is an example post.
- `content/about.md` is an example About page at `/about/`, the URL a WordPress About page usually has. If your WordPress site has no About page, also remove the "About" item from `data/menu.yml`.
- `data/menu.yml` also links to the Roq documentation. Remove that "Documentation" item.
- `content/index.html` is your home page, and Roq reads the site title and description from its front matter. Replace the starter values:
  - `title`, `description` and `name`.
  - `simple-name`, which the theme shows in the footer of every page (`2026 © Roq`).
  - The `social-*` keys, which point to the Quarkus accounts.
  - Below the front matter, the Roq demo content: the `{#roq/hero}` texts and its "Let's Roq the basics" button to iamroq.dev, the four `{#roq/featureCard}` blocks and the "Next steps" section.

Keep `content/blog.html`: it lists your posts at `/blog/`, with pagination.

Then copy the converted content in:

```shell
cp -R ../wordpress-content/posts/. content/posts/
cp -R ../wordpress-content/pages/. content/
```

The converter puts drafts in `_drafts` folders. Roq ignores every file or folder starting with `_`, so your drafts would silently disappear. Rename these folders to `drafts`, the folder Roq treats as drafts:

```shell
[ -d content/posts/_drafts ] && mv content/posts/_drafts content/posts/drafts
[ -d content/_drafts ] && mv content/_drafts content/drafts
```

Drafts stay hidden in the generated site. To see them while you finish them, start the dev server with `roq start -Dsite.draft=true`.

## 4. Fix up the front matter

A few things in the converted files don't fit Roq yet:

- **URLs**: WordPress URLs like `/2018/11/03/block-image/` and nested page URLs like `/about/page-markup-and-formatting/` would change, so every existing link and search result would break.
- **Categories**: the Roq tagging plugin only reads `tags`, so the WordPress categories wouldn't get tag pages. The script adds them to `tags` and keeps the `categories` list as it is.
- **Cover images**: the converter writes the featured image as `coverImage: "photo.jpg"`, while Roq reads `image`, relative to the post folder: `image: "images/photo.jpg"`.
- **Image links**: an image in WordPress often links to its full size file in `wp-content/uploads`, which disappears with the WordPress site.
- **Backslashes**: a title with a backslash, like `Special Characters \;`, is written without escaping it. YAML rejects `\;`, and the Roq build fails with `Failed to parse the YAML front matter block`.

Save this script as `wp-fixup.mjs` in your working folder, next to `export.xml`:

```javascript
// Adapts wordpress-export-to-markdown output to Roq front matter.
// Usage: node wp-fixup.mjs <content-dir> <wordpress-export.xml>
import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

function* markdownFiles(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      yield* markdownFiles(path);
    } else if (name.endsWith('.md')) {
      yield path;
    }
  }
}

// Returns the items of a "key:" block followed by "  - value" items; removes the block unless keep is set
function readList(lines, key, keep = false) {
  const start = lines.findIndex((l) => l.startsWith(`${key}:`));
  if (start < 0) {
    return [];
  }
  let end = start + 1;
  while (end < lines.length && lines[end].startsWith('  - ')) {
    end++;
  }
  const block = keep ? lines.slice(start, end) : lines.splice(start, end - start);
  return block.slice(1).map((l) => l.slice(4));
}

// Maps each WordPress post id to its permalink path, as WordPress published it
function readPermalinks(exportFile) {
  const permalinks = new Map();
  for (const [, item] of readFileSync(exportFile, 'utf8').matchAll(/<item>([\s\S]*?)<\/item>/g)) {
    const id = item.match(/<wp:post_id>(\d+)<\/wp:post_id>/)?.[1];
    const link = item.match(/<link>([^<]+)<\/link>/)?.[1];
    // Unpublished items only have a query link (/?p=123), keep the Roq default link for them
    const url = link && new URL(link);
    if (id && url && !url.search) {
      permalinks.set(id, decodeURIComponent(url.pathname));
    }
  }
  return permalinks;
}

function fixFrontMatter(frontMatter, permalinks) {
  const lines = frontMatter.split('\n')
    // Keep the exact WordPress URL, so existing links and search results keep working
    .map((l) => l.replace(/^id: (\d+)$/, (m, id) => permalinks.has(id) ? `link: "${permalinks.get(id)}"` : ''))
    .filter((l) => l !== '')
    // The WordPress excerpt may contain HTML, Roq uses the description for SEO meta tags
    .map((l) => l.replace(/^excerpt: "(.*)"$/, (m, text) => `description: "${text.replace(/<[^>]+>/g, '')}"`))
    // A backslash not starting a valid escape breaks YAML double-quoted strings (e.g. a title with "\;")
    .map((l) => l.replace(/^(\w+: ".*)$/, (m) => m.replace(/\\(.?)/g, (e, c) => ('\\"'.includes(c) && c ? e : `\\\\${c}`))))
    // Roq reads the cover image from "image", resolved relative to the post folder
    .map((l) => l.replace(/^coverImage: "(.*)"$/, 'image: "images/$1"'));

  // Roq's tagging plugin only reads "tags": merge the WordPress categories into them,
  // and keep "categories" as it is so the data isn't lost
  const categories = readList(lines, 'categories', true);
  const tags = [...new Set([...readList(lines, 'tags'), ...categories])];
  if (tags.length > 0) {
    lines.push('tags:', ...tags.map((t) => `  - ${t}`));
  }
  return lines.join('\n');
}

// Images often link to their full size file in wp-content/uploads, which disappears with the WordPress site:
// point these links to the copy the converter saved next to the post
function fixUploadLinks(body, file) {
  return body.replace(/\]\((https?:\/\/[^/)]+\/wp-content\/uploads\/[^)]*\/([^/)]+))\)/g, (m, url, name) =>
    existsSync(join(dirname(file), 'images', name)) ? `](images/${name})` : m);
}

const [dir, exportFile] = process.argv.slice(2);
if (!dir || !exportFile) {
  console.error('Usage: node wp-fixup.mjs <content-dir> <wordpress-export.xml>');
  process.exit(1);
}
const permalinks = readPermalinks(exportFile);
let count = 0;
for (const file of markdownFiles(dir)) {
  const source = readFileSync(file, 'utf8');
  const match = source.match(/^---\n([\s\S]*?)\n---\n/);
  if (!match) {
    continue;
  }
  const body = fixUploadLinks(source.slice(match[0].length), file);
  const fixed = `---\n${fixFrontMatter(match[1], permalinks)}\n---\n${body}`;
  if (fixed !== source) {
    writeFileSync(file, fixed);
    count++;
  }
}
console.log(`Updated ${count} files`);
```

Run it from the `my-blog` folder:

```shell
node ../wp-fixup.mjs content ../export.xml
```

The script is safe to run twice: a second run changes nothing. A converted post now starts like this:

```yaml
---
link: "/2012/03/15/template-featured-image-vertical/"
title: "Template: Featured Image (Vertical)"
date: 2012-03-15 22:36:32 +0000
slug: "template-featured-image-vertical"
categories: 
  - "classic"
  - "template-2"
image: "images/featured-image-vertical.jpg"
tags:
  - "codex"
  - "edge-case"
  - "featured-image"
  - "image"
  - "template"
  - "classic"
  - "template-2"
---
```

The last two tags, `classic` and `template-2`, are the WordPress categories, which also stay in `categories`.

The script can only keep URLs that have a path. If your site uses the **Plain** setting in **Settings → Permalinks**, WordPress publishes every post as `/?p=123`, and the export contains no path to copy, so every post gets the Roq default URL instead. Old `/?p=123` links can't be kept on a static site: static hosts usually ignore the query string and serve the home page.

> [!TIP]
> Why copy the `link` from the export, instead of setting a link template like `site.collections.posts.link=/:year/:month/:day/:slug/`? WordPress builds the date in the URL from the post's local time, while the converter writes the date in UTC. On the test site, four posts had a local date one day earlier than their UTC date, so a link template gave them a different URL. Copying the `link` keeps every URL exactly as WordPress published it, including nested page URLs, which the converter flattens into top-level folders.

Now that every page has its WordPress URL, look for the pages of your **Settings → Reading** setup. With a static front page, the WordPress front page has the URL `/` and the posts page usually `/blog/`, the URLs of the Roq `index.html` and `blog.html`:

```shell
grep -rlE '^link: "/(blog/)?"$' content
```

On the test site, this found the "Blog" page:

```
content/2011-05-21-blog/index.md
```

The WordPress posts page is an empty placeholder, and the Roq `blog.html` already lists your posts, so delete it. For a WordPress front page, copy the text you want to keep into `content/index.html`, then delete the WordPress folder:

```shell
rm -r content/2011-05-21-blog
```

## 5. Build and preview

Generate the site:

```shell
roq generate
```

If the build still stops with a path conflict, such as `Duplicate path 'blog/' produced by both '2011-05-21-blog/index.md' and 'blog.html'`, two pages have the same URL: delete or rename one of them. Then preview the result:

```shell
roq serve target/roq
```

Posts scheduled for a future date are skipped with a warning such as `Ignoring page 'posts/2030-01-01-scheduled/index.md' because it's scheduled for later`, and appear once their date has passed.

> [!TIP]
> `roq generate` starts your site on port 8080 to render it. If another application already listens on 8080, every page fails with `Response status code 404 is not equal to 200`. Pick another port: `QUARKUS_HTTP_PORT=8796 roq generate`.

## 6. Finish by hand

Search the content for what the converter can't handle. None of it breaks the build, so it is easy to miss.

**Classic editor shortcodes** such as `[caption]`, `[gallery]`, `[audio]` or `[embed]` stay in the text, escaped, and show up as plain text on the page. List the files that contain them:

```shell
grep -rlE '\\\[(caption|gallery|audio|video|embed|playlist)' content
```

Replace each shortcode with Markdown or HTML. On the test site, 8 files needed this.

**Audio, video and other files**: the converter only downloads images. Other files still link to the WordPress site:

```shell
grep -rn 'wp-content/uploads' content
```

Copy these files from `wp-content/uploads` on your server into the post folder and update the links.

**Embeds**: YouTube, Twitter and other embed blocks become the bare URL as text. Replace them with the embed code of the service, for example the YouTube `<iframe>`.

**Block layouts**: columns, galleries, cover blocks and buttons lose their layout. Their text and images are all there, but stacked in a single column. Rebuild the layouts you care about with HTML and CSS in `web/`, or with a Qute template.

**Everything else**:

- **Comments** are not converted. The test export had 34 of them. See [Add comments with a web component](/posts/add-comments-web-component/) or [Add comments with hybrid mode](/posts/add-comments-hybrid/) to accept new ones.
- **Menus** are not converted. Edit `data/menu.yml` to rebuild your navigation.
- **Logo, mascot and favicon** come from the Roq starter site: `image: logo.svg` in the front matter of `content/index.html` and `logo="/images/mascot.svg"` in its hero. Replace the files in `public/images/`, see [Roq the basics](/docs/basics/).
- **Synced patterns** (reusable blocks) are not exported by the converter. The test site didn't use them, so this tutorial doesn't cover them.

## 7. Replace your WordPress plugins

For several common WordPress plugins, Roq has a plugin that does the same job. Add one with `roq add plugin:<name>`:

| WordPress feature or plugin              | Roq plugin                                                                                                                                                    | `roq add` name   |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------- |
| Categories and tags archives             | [Tagging](/plugin/tagging/): a page per tag, used in this tutorial                                                                                            | `plugin:tagging` |
| XML sitemap from an SEO plugin           | [Sitemap](/plugin/sitemap/): `sitemap.xml` for search engines and an HTML sitemap for visitors                                                                | `plugin:sitemap` |
| Search widget                            | [Lunr search](/plugin/lunr-search/): full-text search in the browser, no server needed                                                                        | `plugin:lunr`    |
| Redirection plugin                       | [Aliases](/plugin/aliases/): redirect old URLs to a page, with `aliases:` in the front matter                                                                 | `plugin:aliases` |
| Social sharing images from an SEO plugin | [OG card](/plugin/og-card/): 1200×630 preview images generated from a template                                                                                | `plugin:og-card` |
| Table of contents plugin                 | [TOC](/plugin/toc/): a table of contents from the headings, without JavaScript                                                                                | `plugin:toc`     |
| Post series plugin                       | [Series](/plugin/series/): multi-part series with navigation between the parts                                                                                | `plugin:series`  |
| Scheduled posts                          | [Hybrid](/plugin/hybrid/): when you run the site as a Quarkus application instead of static files, future-dated pages go live on their date without a rebuild | `plugin:hybrid`  |

Follow the setup on each page. Browse [Plugins & Themes](/marketplace/) for the full list.

## 8. Write and edit in the browser with the Roq editor

What you may miss most after WordPress is the admin screen where you write posts. Roq has a browser-based editor for that, in dev mode:

```shell
roq start
```

Press `m` in the terminal, or open the **Roq Editor** card in the Dev UI at `/q/dev-ui`. The editor lists your migrated posts and pages. Open one and you get the content on the left and a front matter panel on the right, with the `link`, `tags` and `image` values the fix-up script wrote.

Two things you will notice on migrated content:

- **Posts with HTML** open with a *Visual Editor Compatibility Warning*: `HTML and/or Qute sections were detected`. The converter keeps some HTML, like `<figure>` with `<figcaption>` for captioned images. Choose **Use Code Editor** to edit these posts as Markdown source, so the HTML stays intact. Posts with plain Markdown open in the visual editor.
- **File name suggestions**: the editor names post folders after the date and the title, so a post titled "WP 6.1 Theme block category" in the folder `2023-01-13-theme-block-category` shows a suggested new name. Clicking **Synchronise page file path** and confirming moves the folder, images included. The URL doesn't change, because it comes from `link:` in the front matter, not from the folder name.

The editor is a preview feature and only runs in dev mode: it edits the files on your machine, and none of it is published. You publish by generating the site again. The editor can also commit, push and pull with git; see [Editor](/docs/advanced/#editor) for this and the other options.

## 9. Switch over

Publish the site with the [Roq GitHub Action or any static host](/docs/publishing/), then point your domain to it. Since every post and page kept its WordPress URL, links from other sites, search results and your own internal links keep working. URLs that only WordPress itself serves, like `/?p=123` short links, `/feed/` or `/wp-admin/`, stop working. Roq generates its own RSS feed; see [RSS](/docs/basics/#rss).
