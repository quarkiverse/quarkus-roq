---
title: "Add Comments to Your Blog with Hybrid Mode (30min)"
slug: add-comments-hybrid
description: "Step-by-step tutorial: add dynamic comments to your Roq blog using hybrid mode, Panache, and Qute templates."
author: ia3andy
tags: [tutorial]
series: roq-blog-lab
date: 2026-07-05 13:00
image: https://images.unsplash.com/photo-1516321318423-f06f85e504b3?q=80&w=1200&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
qute: false
---

Your Roq blog generates static pages. That's great for speed and deployment, but what about dynamic features like comments? You'd normally need a third-party service or a separate backend.

With **hybrid mode**, you can keep your Roq blog and add dynamic content backed by a real database. Pages render on demand with access to live CDI beans, while everything else stays static. Best of both worlds.

In about 30 minutes, you'll add a comment system to your blog with an H2 database, Panache entities, and a form that saves comments per post.

> [!NOTE]
> **Prerequisites:** A working Roq blog from the [blog tutorial](/posts/create-a-blog-with-roq/) or the [from-scratch tutorial](/posts/create-a-blog-from-scratch-with-roq/). You should have at least one blog post.

> [!TIP]
> For the best development experience, install the [Quarkus IDE tooling](https://quarkus.io/guides/ide-tooling) for your editor (VS Code, IntelliJ, or Eclipse). You get config autocompletion, validation, and Qute template completion.


## 1. Add hybrid mode

Roq's hybrid plugin makes pages render dynamically per request instead of being pre-generated at build time. Each page can choose its caching strategy via frontmatter.

Add the hybrid plugin:

```shell
roq add plugin:hybrid
```

Then add the backend dependencies to your `pom.xml`:

```xml
<!-- Database: Hibernate ORM Panache + H2 -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-orm-panache</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-h2</artifactId>
</dependency>

<!-- REST endpoint for form handling -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest</artifactId>
</dependency>
```

Then configure the database in `config/application.properties` (or `src/main/resources/application.properties`):

```properties
quarkus.datasource.db-kind=h2
quarkus.hibernate-orm.database.generation=drop-and-create
```

🚀 Restart dev mode. The app should start with no errors.

🚀🔑 With the hybrid plugin active, every page is now served dynamically. By default, pages use `cache: lazy` caching (rendered on first request, cached for subsequent ones). We'll set `cache: lazy` with a short TTL on the post layout so comments stay fresh.


## 2. Create the Comment entity

Comments need a database model. We'll use a Panache entity, which is Hibernate ORM with a simplified API: public fields, built-in query methods, no boilerplate.

**››› CODING TIME**

Create `src/main/java/io/acme/Comment.java` as a Panache entity with fields for `author`, `content`, `createdAt`, and `postSlug` (to link comments to specific posts). Add a query method to find comments by post slug.

<details>
<summary>See hint</summary>

Extend `PanacheEntity` to get a free `id` field and built-in CRUD methods. Use `@Entity` from Jakarta Persistence. The `postSlug` field links a comment to a blog post by its URL slug. Add a static method `findByPost(String slug)` that uses `list("postSlug", Sort.by("createdAt").descending(), slug)`.

</details>

<details>
<summary>See solution</summary>

Create `src/main/java/io/acme/Comment.java`:

```java
package io.acme;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;

@Entity
public class Comment extends PanacheEntity {

    public String author;
    public String content;
    public LocalDateTime createdAt;
    public String postSlug;

    public static List<Comment> findByPost(String slug) {
        return list("postSlug", Sort.by("createdAt").descending(), slug);
    }
}
```

</details>

🚀 Save and let dev mode pick up the new entity. No errors means Hibernate created the table.


## 3. Make comments available in templates

Qute templates can access CDI beans with the `@Named` annotation. We'll create a bean that provides comments for the current page.

**››› CODING TIME**

Create `src/main/java/io/acme/CommentService.java` as a `@Named` CDI bean with a method that takes a post slug and returns its comments.

<details>
<summary>See hint</summary>

Use `@Named("comments")` and `@ApplicationScoped` so it's accessible in templates as `cdi:comments`. Add a method `forPost(String slug)` that calls `Comment.findByPost(slug)`. Also add a `count(String slug)` method for displaying the comment count.

</details>

<details>
<summary>See solution</summary>

Create `src/main/java/io/acme/CommentService.java`:

```java
package io.acme;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@Named("comments")
@ApplicationScoped
public class CommentService {

    public List<Comment> forPost(String slug) {
        return Comment.findByPost(slug);
    }

    public long count(String slug) {
        return Comment.count("postSlug", slug);
    }
}
```

</details>


## 4. Set post pages to dynamic rendering

For comments to show up, the post layout must render dynamically instead of being pre-generated at build time. We'll use `cache: lazy` with a short TTL so pages are cached but refresh every 30 seconds. This is a good balance: most visitors see a fast cached page, and new comments appear within half a minute.

**››› CODING TIME**

Edit your post layout and add `cache: lazy` with `cache-ttl: 30s` to the frontmatter.

<details>
<summary>See hint</summary>

If you used the default theme (Tutorial 1a), you need to override the post layout by creating `templates/layouts/post.html` with `theme-layout: post`, `cache: lazy`, and `cache-ttl: 30s`. If you built from scratch (Tutorial 1b), just add those fields to your existing `templates/layouts/post.html`.

</details>

<details>
<summary>See solution (default theme)</summary>

Create `templates/layouts/post.html` to override the theme:

```html
---
theme-layout: post
cache: lazy
cache-ttl: 30s
---

{#insert /}
```

</details>

<details>
<summary>See solution (from-scratch theme)</summary>

Edit `templates/layouts/post.html` and add caching to the frontmatter:

```yaml
---
layout: default
cache: lazy
cache-ttl: 30s
---
```

</details>

🚀🔑 `cache: lazy` renders the page on first request, then caches it for the duration of `cache-ttl`. After 30 seconds, the next request gets a fresh render with the latest comments. For development, you can use `cache: false` to see changes instantly.

> [!NOTE]
> Other cache options: `cache: false` (render fresh on every request, good for development) and `cache: startup` (pre-render at startup, cache forever, good for pages that never change).


## 5. Add the comment form and list

Now let's display comments and a submission form on every post. We'll create a reusable partial so the comments section can be included in any layout.

**››› CODING TIME**

Create a `templates/partials/comments.html` partial with a list of existing comments and a form that posts to `/api/comments`. Then include it in your post layout.

<details>
<summary>See hint</summary>

Use `{#for comment in cdi:comments.forPost(page.slug)}` to iterate over comments for the current post. The form should use `method="POST"` with `action="/api/comments"` and include hidden fields `postSlug` and `redirectUrl`. Then include the partial in `post.html` with `{#include partials/comments /}`.

</details>

<details>
<summary>See solution</summary>

Create `templates/partials/comments.html`:

```html
<!-- Comments section -->
<section class="mt-12 border-t border-slate-200 dark:border-slate-700 pt-8">
  <h2 class="text-xl font-bold text-slate-900 dark:text-white mb-6">
    Comments ({=cdi:comments.count(page.slug)})
  </h2>

  <!-- Comment list -->
  <div class="space-y-4 mb-8">
    {#for comment in cdi:comments.forPost(page.slug)}
    <div class="p-4 rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700">
      <div class="flex items-center justify-between mb-2">
        <span class="font-medium text-sm text-slate-800 dark:text-slate-200">{=comment.author}</span>
        <time class="text-xs text-slate-500 dark:text-slate-400">{=comment.createdAt.format('MMM d, yyyy HH:mm')}</time>
      </div>
      <p class="text-sm text-slate-700 dark:text-slate-300">{=comment.content}</p>
    </div>
    {#else}
    <p class="text-sm text-slate-500 dark:text-slate-400 italic">No comments yet. Be the first!</p>
    {/for}
  </div>

  <!-- Comment form -->
  <form method="POST" action="/api/comments" class="space-y-4">
    <input type="hidden" name="postSlug" value="{=page.slug}">
    <input type="hidden" name="redirectUrl" value="{=page.url}">
    <div>
      <label for="author" class="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">Name</label>
      <input type="text" id="author" name="author" required
             class="w-full px-3 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-sky-500">
    </div>
    <div>
      <label for="content" class="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">Comment</label>
      <textarea id="content" name="content" rows="3" required
                class="w-full px-3 py-2 rounded-lg border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-sky-500"></textarea>
    </div>
    <button type="submit"
            class="px-4 py-2 rounded-lg bg-sky-600 text-white font-medium hover:bg-sky-700 transition-colors cursor-pointer">
      Post comment
    </button>
  </form>
</section>
```

Then update `templates/layouts/post.html` (default theme):

```html
---
theme-layout: post
cache: lazy
cache-ttl: 30s
---

{#insert /}
{#include partials/comments /}
```

Or for the from-scratch theme, add `{#include partials/comments /}` after your post content in `templates/layouts/post.html`.

</details>

🚀 Refresh a blog post. You should see a "Comments (0)" section with a form at the bottom. The form won't work yet since we haven't created the endpoint.


## 6. Create the form handler

The comment form submits via POST. We need a REST endpoint that saves the comment and redirects back to the post (the POST/redirect/GET pattern).

**››› CODING TIME**

Create `src/main/java/io/acme/CommentResource.java` as a JAX-RS resource that handles the form POST at `/api/comments`.

<details>
<summary>See hint</summary>

Use `@Path("/api/comments")` and a `@POST` method with `@Consumes(MediaType.APPLICATION_FORM_URLENCODED)`. Receive form fields with `@FormParam`. Create a new `Comment`, set its fields including `createdAt = LocalDateTime.now()`, call `.persist()`, and return `Response.seeOther(URI.create(redirectUrl))` to redirect back. Mark the method `@Transactional`.

</details>

<details>
<summary>See solution</summary>

Create `src/main/java/io/acme/CommentResource.java`:

```java
package io.acme;

import java.net.URI;
import java.time.LocalDateTime;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/comments")
public class CommentResource {

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Transactional
    public Response addComment(@FormParam("author") String author,
                               @FormParam("content") String content,
                               @FormParam("postSlug") String postSlug,
                               @FormParam("redirectUrl") String redirectUrl) {
        Comment comment = new Comment();
        comment.author = author;
        comment.content = content;
        comment.postSlug = postSlug;
        comment.createdAt = LocalDateTime.now();
        comment.persist();
        return Response.seeOther(URI.create(redirectUrl)).build();
    }
}
```

</details>

🚀 Go to a blog post, fill in the form, and click "Post comment". The page should reload and show your comment!

🤩 You just added dynamic comments to a static site generator. The post page renders dynamically with `cache: lazy`, queries the database for comments, and displays them. The form saves to H2 via Panache, and the redirect brings you right back to the post.


## 7. Add sample data for development

It's nice to have some comments pre-loaded in dev mode so you can see how the layout looks with content.

**››› CODING TIME**

Create a startup observer that seeds the database with a few sample comments in dev mode.

<details>
<summary>See hint</summary>

Use a `@Startup` CDI bean with `@Transactional`. Check for `LaunchMode.DEVELOPMENT` before inserting. Create 2-3 comments with different post slugs matching your existing posts.

</details>

<details>
<summary>See solution</summary>

Create `src/main/java/io/acme/SampleData.java`:

```java
package io.acme;

import java.time.LocalDateTime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class SampleData {

    @Transactional
    void onStart(@Observes StartupEvent event) {
        if (LaunchMode.current() != LaunchMode.DEVELOPMENT) {
            return;
        }

        Comment c1 = new Comment();
        c1.author = "Ada";
        c1.content = "Great first post! Welcome to the blogging world.";
        c1.postSlug = "hello-world";
        c1.createdAt = LocalDateTime.now().minusHours(2);
        c1.persist();

        Comment c2 = new Comment();
        c2.author = "Grace";
        c2.content = "I love how simple Roq makes this. Nice work!";
        c2.postSlug = "hello-world";
        c2.createdAt = LocalDateTime.now().minusHours(1);
        c2.persist();
    }
}
```

</details>

🚀 Restart dev mode. Navigate to your first post. You should see the sample comments already there.


## What you've learned

- **Hybrid mode** turns Roq from a static site generator into a dynamic server with smart caching
- **`cache: lazy`** with `cache-ttl` renders pages dynamically with smart caching
- **Panache entities** give you a database model with zero boilerplate
- **`@Named` beans** make Java services available in Qute templates via `cdi:` prefix
- **POST/redirect/GET** is the standard pattern for form handling in server-rendered apps

## What's next?

**Next in the series:** [Add Comments with a Web Component](/posts/add-comments-web-component/) for an alternative approach using a Lit web component and a separate microservice.

- **Add validation**: use `@NotBlank` on form fields and show error messages
- **Add HTMX**: replace the full page reload with `hx-post` for instant comment submission (see the [Quarkus Web Lab](https://github.com/quarkusio/quarkus-web-lab) for HTMX patterns)
- **Switch to PostgreSQL**: replace H2 with `quarkus-jdbc-postgresql` for production
- **Add moderation**: add an `approved` boolean field and only show approved comments
- **Try `cache: false`**: for instant comment visibility during development, switch to `cache: false` (renders fresh on every request)
