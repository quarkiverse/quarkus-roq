---
title: "Set It in Roq: The Editor that change the game!"
image: https://images.unsplash.com/photo-1594643469650-dd506331ff7a?q=80&w=2940&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
tags: guide, cool-stuff
description: Roq introduces a TipTap-powered editor with Markdown support, transforming it from a static site generator into a lightweight, developer-friendly CMS. Create, edit, and preview content seamlessly within the Quarkus dev experience.
date: 2026-02-02 00:00:00 +0100
---
Roq started as a solid foundation for building modern apps and static sites. But now, it’s leveling up in a big way. With the introduction of a **TipTap-powered Editor with Markdown support**, Roq is no longer just an SSG tool, it’s stepping into **CMS territory**.

![](the-roq-editor.gif)

## Why This Is a Big Deal

Until now, writers had to:

- Use an IDE or a text editor.
- Manually create new article files.
- Manually open the article preview.
- Use Markdown as code

Now, with Roq’s built-in editor:

- **Native integration**: all integrated in Quarkus dev experience.
- **Rich Text Editor with Markdown support**: write your content in a notion like editor, render beautifully.
- **Preview article**: directly from the editor or using a new tab.

This makes Roq feel less like a static site generator and more like a **developer-friendly CMS**, closer to the flexibility of WordPress — but without the heavyness.

## Key Features

- **Rich formatting**: Bold, italic, headings, lists.
- **Markdown support**: Switch between rich text and Markdown seamlessly.
- **Code editor with syntax highlighting**: For HTML and AsciiDoc content.
- **Media embedding**: Images, links, and more.

## How to Try It

The editor is natively integrated into Roq `2.1`.

```
roq create my-blog
```


Start it

```
roq
```

🚀 Hit `a` (like Admin) to Open [The Roq Editor.](http://localhost:8080/q/dev-ui/quarkus-roq-editor/roq-editor)
