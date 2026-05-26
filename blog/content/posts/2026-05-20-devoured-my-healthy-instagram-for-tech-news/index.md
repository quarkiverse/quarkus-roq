---
title: "Devoured: My Healthy Instagram for Tech News"
description: How I built a daily AI-curated tech digest with Roq, replacing doomscrolling with something actually useful.
image: https://images.unsplash.com/photo-1532356884227-66d7c0e9e4c2?q=80&w=1200&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
tags: cool-stuff, tutorial
author: ia3andy
date: 2026-05-20 10:00:00 +0200
---

I wanted to stay up to date in this new AI era where everything is changing so fast. But there is *so much* information that keeping up feels impossible.

I saw the <a href="https://tldr.tech/" target="_blank">TLDR newsletter</a> popping up on Instagram and thought it looked promising. But I'm not a fan of email newsletters, and I found the format hard to digest.

Then it hit me: I spend way too much time on Instagram swiping through content. What if I could channel that energy into something actually useful? What if I could build my own **healthy Instagram** for tech news, powered by AI and <a href="https://pages.quarkiverse.io/quarkus-roq/" target="_blank">Roq</a>?

That's how <a href="https://devoured.fyi" target="_blank">devoured.fyi</a> was born.

## The idea

Take the best tech feeds, let AI digest them even further, remove ads and noise, and serve it all as a clean, swipeable daily digest. Roughly 15 to 20 condensed articles per day, incremental enough that I can skim the one-liners but still dive deeper when something catches my eye.

## How it works

A <a href="https://github.com/ia3andy/devoured/blob/main/.github/workflows/daily-digest.yml" target="_blank">GitHub Action</a> runs every morning. It fetches RSS feeds from multiple sources (AI, Tech, DevOps, Data, Design), processes them through a <a href="https://github.com/ia3andy/devoured/blob/main/scripts/DigestHelper.java" target="_blank">JBang script</a>, and uses the **Gemini free tier** to generate structured summaries for each article. The output is a JSON file per day, committed directly to the repository.

It took a while to tweak the GitHub Action to perfection, mainly to avoid going over the Gemini free tier limits. I ended up using <a href="https://smallrye.io/smallrye-mutiny/" target="_blank">Mutiny</a> for reactive batching with controlled concurrency, combined with caching and careful rate limiting.

Gemini initially gave pretty poor quality results. I spent a lot of time comparing its output against Claude's to identify the gaps, then slowly iterated on the prompts until Gemini's summaries reached a quality level comparable to Claude. It's a good reminder that prompt engineering matters as much as model choice.

## Roq data as the engine

This is where Roq shines. Each daily digest is a JSON file in the `data/digest-posts/` directory (e.g. `data/digest-posts/2026-05-05.json`). Roq's [data feature]({=site.page('docs/basics.adoc').url}#_data_directories) turns that directory into **two collections automatically**: one aggregated collection containing all digests, and individual entries for each day. No Java code needed for the basic setup, just drop JSON files and they're available in your templates.

Each JSON file contains frontmatter-like fields (`title`, `date`, `layout`, `tags`) alongside the digest content (`sections` with articles, summaries, one-liners, and decoder entries). Roq maps it all to template variables accessible through Qute:

```json
{
  "title": "Devoured - May 05, 2026",
  "layout": "digest-post",
  "date": "2026-05-05",
  "sections": [
    {
      "name": "AI",
      "articles": [
        {
          "title": "Anthropic and OpenAI Launch Enterprise AI Ventures",
          "one-liner": "Both are launching enterprise AI joint ventures...",
          "summary": { "what": "...", "why": "...", "takeaway": "..." }
        }
      ]
    }
  ]
}
```

## Making static feel dynamic with localStorage

One challenge with a static site: how do you track what the user has already read? The answer is **localStorage**. Devoured uses it to remember which articles you've seen, your preferred sections, and your reading preferences. All client-side, no backend, no accounts. The site feels dynamic and personalized while remaining fully static.

## The result

After weeks of tweaking the prompts, the visual layout, and the content density, I now have exactly what I wanted: a quick daily swipe through the most relevant tech news, condensed by AI, with the option to go deeper on anything interesting.

It's free, open source, and built entirely with Roq.

**Links:**

- <a href="https://devoured.fyi" target="_blank">devoured.fyi</a>
- <a href="https://github.com/ia3andy/devoured" target="_blank">Source on GitHub</a>
- [Roq data documentation]({=site.page('docs/basics.adoc').url}#_data_directories)
- <a href="https://tldr.tech/" target="_blank">TLDR newsletter</a> (the original inspiration)
