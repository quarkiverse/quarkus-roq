---
title: "Easily manage Drafts and Future articles in Roq"
date: 2024-09-19 10:45:00 +0200
description: Roq SSG introduces a new feature that allows you to hide or show draft and future articles using simple Quarkus configurations. This update gives developers greater control over which content is visible, improving content management and workflow.
image: image.jpg
tags: new-feature
author: ia3andy
---

Roq just made content management easier with a cool new feature that lets you control drafts and future articles directly in your configuration. No more messing around with hard-to-track content—now you can manage everything through the Quarkus config:

```shell
roq -Dsite.draft -Dsite.future
```

This is using frontmatter data in articles and pages `draft: true` and `date: 2024-09-19 10:45:00 +0200` to take the decision.

By default, both options are set to `false`, meaning that drafts and future pages will stay hidden until you’re ready to reveal them. All you need to do is update these configs when you're ready to publish.

This simple feature adds flexibility and control, making your publishing process more streamlined. Happy content managing!
