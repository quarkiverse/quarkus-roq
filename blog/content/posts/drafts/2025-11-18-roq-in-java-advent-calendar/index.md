---
title: "Cozy Coding by the Fire: learn to Create A Beautiful Static Websites with Roq, Java & TailwindCSS"
description: >-
  Put a blanket on your lap and enjoy this Advent treat! I’ll guide you step by step to create a beautiful static site with Roq, leveraging the comfort of Quarkus Dev Mode. We’ll start with the page layout, then add a responsive menu, multiple pages, and a blog section. With TailwindCSS for styling—including dark/light mode and full responsiveness—your site will look delightful without much effort.
image: https://images.unsplash.com/photo-1585776245991-cf89dd7fc73a?q=80&w=3999&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
tags: blogging
---

Prep a warm drink and put on some soft music, in this tutorial, we’ll show you how to create a modern static website using Roq — a powerful new tool that combines Java, Quarkus, and TailwindCSS. You’ll build a site with a responsive menu, a stunning hero section, and an article listing, all while enjoying the simplicity and speed of static site generation. Let’s make coding cozy and future-ready!

Make sure you have the JDK 17+ on your machine and install the [Quarkus CLI](https://quarkus.io/guides/cli-tooling) to makes things smoother:
```
curl -Ls https://sh.jbang.dev | bash -s - trust add https://repo1.maven.org/maven2/io/quarkus/quarkus-cli/
curl -Ls https://sh.jbang.dev | bash -s - app install --fresh --force quarkus@quarkusio
```

NOTE: We started working on a Quarkus Wrapper to allow starting dev-mode and soon also editor mode without anything to install on the machine.

I cooked a starter repo with Quarkus, Roq and Tailwind as pom.xml dependencies and the very base of your next website:
```shell
# Clone the starter repo (or download):
git clone ...
cd ...
```

You should be all set for the whole journey 👌

What did I clone 🤨? 
```
the-coder-site/
├── content/index.html            # Website index page and metadata
├── public/images/                # A few images
├── web/
│   ├── *.js                      # Scripts (auto-bundled)
│   └── *.css                     # Styles (auto-bundled)
├── templates/
│   ├── layouts/
│   │   ├── default.html          # Base HTML structure
│   │   ├── post.html             # Layout for a blog post
│   │   └── page.html             # Layout for a page
│   └── partials/
│       ├── header.html           # Empty page header
│       └── footer.html           # Empty page footer
├── config/application.properties # Site config 
├── pom.xml                       # Quarkus setup (Roq, TailwindCSS)
├── stuff/                        # Stuff for later (blog posts, etc.)
```

Let's start Quarkus Dev-Mode:
```
quarkus dev
```

When Quarkus is started, yeah... well... after downloading a bunch of dependencies 😅 (just the first time), press `w` on you keyboard and let the magic happen!
















