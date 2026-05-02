---
title: QR Code
description: Embed auto-generated QR codes for any URL or custom text
layout: marketplace-plugin
icon: fa-solid fa-qrcode
install-name: qrcode
tags: [media]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/qrcode
---

Add QR codes to your website. Create a template and add the `#qrcode` tag to it, then style and size it as you want.

By default, the plugin produces HTML output compatible with both HTML and Markdown templates. To use the plugin with AsciiDoc, set the `asciidoc` attribute to `true`.

{|
```html
{#qrcode value="https://luigis.com/menu/" alt="Luigi's Menu" foreground="#000066" background="#FFFFFF" width=300 height=300 /}

{#qrcode value="https://luigis.com/menu/" alt="Luigi's Menu" foreground="#000066" background="#FFFFFF" width=300 height=300 asciidoc=true /}
```
|}