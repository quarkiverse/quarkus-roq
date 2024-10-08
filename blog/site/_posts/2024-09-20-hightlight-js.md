---
layout: post
title: "How to add syntax highlighting to your Roq site"
date: 2024-09-20 11:00:00 +0200
description: Learn how to integrate syntax highlighting into your Roq site using Highlight.js and the Quarkus web-bundler extension. This guide walks you through the simple steps to add it via the pom.xml, JavaScript, and SCSS files.
img: https://images.unsplash.com/photo-1563206767-5b18f218e8de?q=80&w=3538&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
tags: styling, frontmatter, guide, cool-stuff
author: ia3andy
---

Adding syntax highlighting to your Roq project has never been easier. Here’s a quick guide to help you integrate **Highlight.js** in your project with the help of the Quarkus web-bundler extension.

### Step 1: Add the Quarkus Web-Bundler

First, you’ll need to add the [Quarkus Web-Bundler](https://github.com/quarkiverse/quarkus-web-bundler) to your project. This tool will bundle your JavaScript and SCSS resources.

### Step 2: Add Highlight.js Dependency

Next, add **Highlight.js** to your `pom.xml` like this:

```xml
<dependency>
    <groupId>org.mvnpm</groupId>
    <artifactId>highlight.js</artifactId>
    <version>11.10.0</version>
    <scope>provided</scope>
</dependency>
```

This will make the Highlight.js library available to your project.

### Step 3: Initialize Highlight.js

Now, let’s configure Highlight.js. In your `src/main/resources/web/app/main.js`, import the library and activate it:

```javascript
import hljs from 'highlight.js';

hljs.highlightAll();
```

### Step 4: Style Your Syntax Highlighting

To style the code blocks, import the Highlight.js default theme into your SCSS file. Add this to your `src/main/resources/web/app/main.scss`:

```scss
@import 'highlight.js/scss/default.scss';
```

And that's it! Now your code blocks will be beautifully highlighted, adding a more polished and professional look to your content.

This process is quick and effective, making it easy to provide clear, readable syntax highlighting for your users. Happy coding!
