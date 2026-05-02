---
title: Sass
description: Write stylesheets with Sass variables, nesting, and mixins for your Roq site
layout: marketplace-web
icon: fa-brands fa-sass
tags: [css, styling]
source: https://github.com/quarkiverse/quarkus-web-bundler/tree/main/sass
---

Add Sass/SCSS support to your Roq project. Use variables, nesting, mixins, and all Sass features to write maintainable stylesheets.

Sass is the default Web Bundler preprocessor. It is included automatically when using the Web Bundler without Tailwind.

### Getting started

Create `.scss` files in your `web/` directory:

{|
```scss
// web/style.scss
$primary: #3b82f6;
$radius: 0.5rem;

.card {
  border-radius: $radius;
  background: white;

  &-title {
    color: $primary;
    font-weight: 600;
  }
}
```
|}