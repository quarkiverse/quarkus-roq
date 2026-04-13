---
title: mvnpm
description: Use npm packages as Maven dependencies with zero Node.js installation required
layout: marketplace-web
icon: fa-brands fa-npm
tags: [npm, dependencies]
source: https://github.com/quarkiverse/quarkus-web-bundler/tree/main/mvnpm
---

Add [mvnpm](https://mvnpm.org) support to your Roq project. Import npm packages directly through Maven coordinates, with no Node.js or npm installation required.

mvnpm bridges Maven and npm, converting npm packages into Maven artifacts that the Web Bundler can resolve and bundle.

### Getting started

Add npm packages as Maven dependencies in your `pom.xml`:

{|
```xml
<dependency>
    <groupId>org.mvnpm</groupId>
    <artifactId>htmx.org</artifactId>
    <version>2.0.4</version>
    <scope>provided</scope>
</dependency>
```
|}

Then import them in your JavaScript:

{|
```javascript
import 'htmx.org';
```
|}

Browse available packages at [mvnpm.org](https://mvnpm.org).