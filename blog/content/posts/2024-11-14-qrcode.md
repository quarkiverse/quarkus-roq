---
title: Need a QR Code?
image: https://images.unsplash.com/photo-1726255294277-57c46883bd94?q=80&w=3870&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
description: Add a QR Code to your Roq website.
author: ia3andy
tags: guide,cool-stuff,plugin
date: 2024-11-14 14:00:00 +0200
series: roq-plugins
---

Need to add a scannable QR Code to your website? Whether it's for a restaurant menu, event ticket, or any other use case where you want to make your content easily accessible via mobile devices, the Roq QR Code plugin has you covered.

**Step 1:** Add the QRCode plugin in your dependencies file:

```xml
 <dependency>
    <groupId>io.quarkiverse.roq</groupId>
    <artifactId>quarkus-roq-plugin-qrcode</artifactId>
    <version>...</version>
</dependency>
```

**Step 2:** Add the QRCode tag to your template with all the parameters you need:

```html
\{#qrcode value="https://luigis.com/menu/" alt="Luigi's Menu" foreground="#000066" background="#FFFFFF" width=300 height=300 /}
```

It will render a QR Code like this:

<div data-raw>
<div style="text-align: center">
{#qrcode value="https://luigis.com/menu/" alt="Luigi's Menu" foreground="#000066" background="#FFFFFF" width=300 height=300 /}
</div>
</div>
