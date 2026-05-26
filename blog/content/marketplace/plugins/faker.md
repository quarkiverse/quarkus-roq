---
title: Faker
description: Generate fake blog posts with realistic content for development and testing
layout: marketplace-plugin
icon: fa-solid fa-wand-magic-sparkles
install-name: faker
tags: [development, testing]
source: https://github.com/quarkiverse/quarkus-roq/tree/main/roq-plugin/faker
search-boost: 20
---

Populate your site with realistic fake content during development. Generates posts with random titles, descriptions, authors, dates, tags, and images so you can test layouts, pagination, and styling without writing real content.

### Configuration

Add the number of fake documents to generate per collection in `application.properties`:

```properties
quarkus.roq.faker.count.posts=20
```

This generates 20 fake posts in the `posts` collection. You can target any collection:

```properties
quarkus.roq.faker.count.posts=20
quarkus.roq.faker.count.docs=5
```

### What's Generated

Each fake document includes:

| Field | Content |
|---|---|
| `title` | Random book title with genre and author |
| `description` | Random sentence |
| `author` | Random author name |
| `date` | Random date within the last 2 years |
| `tags` | 1 to 4 random tags from a curated list |
| `image` | Random image from a set of 10 bundled photos |
| `content` | 2 to 5 paragraphs of lorem ipsum |

The generated posts use the collection's configured layout (e.g., `post` for the `posts` collection) and respect all theme styling.

### Dev Mode Only

Faker is designed for development. The generated content is not persisted to disk. It exists only in the dev server's memory and is regenerated on each restart.
