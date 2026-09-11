---
title: "Pwned: How Roq Saved the Day"
date: 2026-09-11
description: How AlpesJUG went from a defaced WordPress site and 17 years of lost data to a new, live community website in a few hours with Roq.
tags: blogging, community, recovery
image: roq-save-the-day.webp
---

On September 10, while preparing the next AlpesJUG meetup after a long, hot summer, we discovered the worst possible surprise: our manually hosted WordPress site had been defaced.

Worse still, all of its data was gone. That meant losing 17 years of community history--our website dates back to 2009--just when we needed it to announce the next event.

We did not have days to recover. We needed a working website, fast.

## A fast way back online

I had already used Roq for a few project websites and knew that other meetup communities used it too. Its simplicity made it the obvious choice: we could build a new site quickly, keep the content in Git, and deploy it as a static site.

Clément De Tastes generously shared the `toulon.dev` repository, giving us a solid starting point for a technology-meetup website. After some basic restyling, I pushed the initial version online with GitHub Pages, powered by Roq's GitHub Action.

The first version was live, but it was also very bare: it had only a single post. Getting online was the first win; rebuilding the community's history was the next challenge.

## Rebuilding our event history

AlpesJUG uses [Meetup](https://www.meetup.com/fr-FR/alpesjug/) to manage event registrations. While creating the event page, I noticed that many previous events were still listed there.

Using a little AI and site scraping, I recreated the event announcements back to 2022. With Roq, turning that recovered information into pages was quick: write the content, commit it, and the site is ready to publish.

## From defaced to live in a few hours

After following the Roq instructions to configure GitHub Pages and updating a few DNS records, AlpesJUG was back online with a functional website only a few hours after we had lost everything.

Roq did not bring back the original data, but it gave us a fast, maintainable foundation to get the community moving again. The new site is static, versioned in Git, and far easier to recover if disaster strikes again.

For us as JUG leaders, Roq's Git-based workflow is also a much more natural fit than a traditional CMS. We are developers: writing content in files, reviewing changes, and publishing through commits and pull requests are tools and habits we already use every day.

On September 23, we will welcome José Paumard for our next meetup--with a brand-new website ready to share the announcement.

Thank you to Roq--and to Andy Damevin and Frédéric Blanc for their great workshop at [Riviera DEV](https://riviera.dev/). A special thank you to Clément De Tastes for sharing the starting point that helped save the day.
