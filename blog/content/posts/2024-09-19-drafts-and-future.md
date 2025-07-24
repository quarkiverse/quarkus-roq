---
title: "Easily manage Drafts and Future articles in Roq"
date: 2024-09-19 10:45:00 +0200
description: Roq SSG introduces a new feature that allows you to hide or show draft and future articles using simple Quarkus configurations. This update gives developers greater control over which content is visible, improving content management and workflow.
image: https://plus.unsplash.com/premium_photo-1664197369206-597ffd51f65c?q=80&w=3540&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
tags: frontmatter, guide
author: ia3andy
---

Roq just made content management easier with a cool new feature that lets you control drafts and future articles directly in your configuration. No more messing around with hard-to-track content—now you can manage everything through the Quarkus config:

```shell
quarkus dev -site.draft -site.future`
```

This is using frontmatter data in articles and pages `draft: true` and `date: 2024-09-19 10:45:00 +0200` to take the decision.

By default, both options are set to `false`, meaning that drafts and future pages will stay hidden until you’re ready to reveal them. All you need to do is update these configs when you're ready to publish.

This simple feature adds flexibility and control, making your publishing process more streamlined. Happy content managing!
