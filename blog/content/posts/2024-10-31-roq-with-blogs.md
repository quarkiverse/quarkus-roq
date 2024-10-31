---
layout: :theme/post
title: "Roq with Blogs"
description:  🚀 Roq 1.0 is ON! It is time to give it a shot and give us feedback 🚀
image: https://images.unsplash.com/photo-1458501534264-7d326fa0ca04?q=80&w=3540&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
tags: blogging
author: ia3andy
---

Hello folks,

First let me thanks the Roq [contributors]({site.url('about')}), they have been awesome and this has been so fun to create Roq!

**If you want to get started quickly:**

[Click here](https://docs.quarkiverse.io/quarkus-roq/dev/index.html#:~:text=Click%20here%20to,roq%0Aquarkus%20dev) to generate your Roq Starter App.

or use the [Quarkus CLI](https://docs.quarkiverse.io/quarkus-roq/dev/index.html#:~:text=or%20use%20the-,Quarkus%20CLI,-%3A):
```shell
quarkus create app blog-with-roq -x=io.quarkiverse.roq:quarkus-roq
```

Then

```shell
cd blog-with-roq
quarkus dev
```

**If you have a bit of time, with this release, I think it's time for me to give you the full story 📖:**

It all started a while back when I helped my wife create [her blog](https://www.masupercoach.fr/). After reviewing a few options, I decided to use Jekyll, as it was the easiest solution with GitHub Pages. Over time, I grew quite frustrated with the process:

- It was hard for my wife to install and start using.
- It was challenging to maintain and keep updated.
- Using Ruby didn’t feel great.
- Plugins were often outdated or unmaintained.

Then my wife said:

> **My wife**: “But why don’t you use your famous Quarkus?”
>
> **Me**: “This is not the right tool to create a blog 😭”

I think this was around the time Quarkus 1.0 was being released...

... 😴 Time passes ...

🗓️ **Mar 23, 2022:** [quarkus-quinoa](https://github.com/quarkiverse/quarkus-quinoa/)

🗓️ **Feb 3, 2023:** [quarkus-web-bundler](https://github.com/quarkiverse/quarkus-web-bundler/)

🗓️ **Early 2024:** [Quarkus web guide](https://quarkus.io/guides/web)

At this point, I thought back on what my wife had said... maybe it was time to reconsider? But Qute processes things at runtime, so it didn’t seem possible 😤

... 😴 Time passes ...

🗓️ **May 7, 2024:**

![Discussion with Max]({page.image('generator-runtime-discussion.png')})

My idea was to generate static pages at runtime… because then all of Quarkus could become static without any changes 😍.

🗓️ **May 17, 2024:** [quarkus-roq (generator part)](https://github.com/quarkiverse/quarkus-roq/)

At this point, I thought we (mostly) had everything in Quarkus to change my answer to my wife 🤓

For those who wonder, "Roq" was chosen because: `static = rock, rock + quarkus = roq`

🗓️ **June 19, 2024:** [Roq Focus Group](https://github.com/quarkusio/quarkus/discussions/41309)

And now, thanks to the awesome team 🧑‍💻👩🏻‍💻!

🗓️ **October 31, 2024:** **Roq 1.0**

🎉🍾🥂

If you like the idea, support us, give us a star ⭐ or start contributing... 


