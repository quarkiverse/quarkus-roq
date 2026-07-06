---
title: "Add Comments to Your Blog with a Web Component (30min)"
slug: add-comments-web-component
description: "Step-by-step tutorial: build a Lit web component for comments backed by a Quarkus REST API on your Roq blog."
author: ia3andy
tags: [tutorial]
series: roq-blog-lab
draft: true
date: 2026-07-05 14:00
image: https://images.unsplash.com/photo-1555421689-491a97ff2040?q=80&w=1200&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D
qute: false
---

Your Roq blog generates static pages. Fast, simple, easy to deploy. But what about comments? You could use a third-party widget, but why not build your own?

In this tutorial, you'll create a **Lit web component** for comments backed by a **Quarkus REST API** in a separate microservice. The blog pages stay static. The comments are fully dynamic, loaded and posted via JavaScript from a dedicated comments server, no page reload needed.

This is the "web component" approach (as opposed to the [hybrid mode approach](/posts/add-comments-hybrid/)). Same result, different architecture: here, the blog stays 100% static and the interactivity lives in a custom HTML element served by a separate Quarkus app.

> [!NOTE]
> **Prerequisites:** A working Roq blog from the [blog tutorial](/posts/create-a-blog-with-roq/) or the [from-scratch tutorial](/posts/create-a-blog-from-scratch-with-roq/). You should have at least one blog post. Keep the blog running on port 8080.

> [!TIP]
> For the best development experience, install the [Quarkus IDE tooling](https://quarkus.io/guides/ide-tooling) for your editor (VS Code, IntelliJ, or Eclipse). You get config autocompletion, validation, and Qute template completion.


## 1. Create the comments project

The comments service is a separate Quarkus app that runs alongside your blog. Generate it from [code.quarkus.io](https://code.quarkus.io/?a=comments&e=rest-jackson&e=hibernate-orm-panache&e=jdbc-h2&e=io.quarkiverse.web-bundler%3Aquarkus-web-bundler) with the extensions: REST Jackson, Hibernate ORM Panache, JDBC H2, and Web Bundler. Extract it next to your blog directory.

Then add the Lit dependency to `comments/pom.xml`:

```xml
<!-- Lit web component library (bundled by Web Bundler) -->
<dependency>
    <groupId>org.mvnpm</groupId>
    <artifactId>lit</artifactId>
    <version>3.2.1</version>
    <scope>provided</scope>
</dependency>
```

Then configure the app in `src/main/resources/application.properties`:

```properties
quarkus.http.port=7070
quarkus.datasource.db-kind=h2
quarkus.hibernate-orm.database.generation=drop-and-create
quarkus.http.cors=true
quarkus.http.cors.origins=http://localhost:8080,http://localhost:7070
```

The comments app runs on port 7070 so it doesn't conflict with your blog. CORS is enabled so the blog (on port 8080) can load the web component script and call the API.

If you don't have the Quarkus CLI yet, install it with JBang (same as Roq):

```shell
jbang app install --fresh --force quarkus@quarkusio
```

Start the comments app in dev mode:

```shell
cd comments
quarkus dev
```

🚀 The app should start on port 7070 with no errors.

> [!NOTE]
> The Lit dependency has `scope: provided` because Web Bundler bundles the JavaScript at build time. The library isn't needed at runtime in the Java classpath, only during the bundling step. This is how [mvnpm](https://mvnpm.org) works: npm packages as Maven dependencies, bundled via esbuild.


## 2. Create the Comment entity

Just like in the hybrid tutorial, we need a database model for comments. Panache makes this simple.

**››› CODING TIME**

Create `src/main/java/io/acme/Comment.java` as a Panache entity with `author`, `content`, `createdAt`, and `postSlug` fields. Add a query method to find comments by post slug, and a method that persists a new comment and returns the updated list.

<details>
<summary>See hint</summary>

Extend `PanacheEntity` for a free `id` field and CRUD methods. Use `@Entity` from Jakarta Persistence. Add `findByPost(String slug)` using `list("postSlug", Sort.by("createdAt").descending(), slug)`. The "persist and return list" method is useful for the API to return the updated comment list after a POST.

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


## 3. Create the REST API

The web component will talk to a REST API. We need two endpoints: GET to fetch comments for a post, and POST to add a new one.

**››› CODING TIME**

Create `src/main/java/io/acme/CommentResource.java` with a GET endpoint at `/api/comments/{postSlug}` and a POST endpoint at `/api/comments`. The POST should return the updated list of comments so the web component can refresh immediately.

<details>
<summary>See hint</summary>

Use `@Path("/api/comments")`, `@GET` with `@Path("{postSlug}")` for fetching, and `@POST` with `@Consumes(APPLICATION_JSON)` for creating. Mark the POST method `@Transactional`. Set `createdAt = LocalDateTime.now()` before persisting. Return `Comment.findByPost(comment.postSlug)` after persisting so the client gets the full updated list.

</details>

<details>
<summary>See solution</summary>

Create `src/main/java/io/acme/CommentResource.java`:

```java
package io.acme;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/comments")
@Produces(MediaType.APPLICATION_JSON)
public class CommentResource {

    @GET
    @Path("{postSlug}")
    public List<Comment> getComments(@PathParam("postSlug") String postSlug) {
        return Comment.findByPost(postSlug);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public List<Comment> addComment(Comment comment) {
        comment.id = null;
        comment.createdAt = LocalDateTime.now();
        comment.persist();
        return Comment.findByPost(comment.postSlug);
    }
}
```

</details>

🚀 Test the API with curl:

```shell
curl -s http://localhost:7070/api/comments/hello-world | jq .
```

You should get an empty JSON array `[]`. The API works!


## 4. Build the Lit web component

This is where it gets fun. We'll create a custom HTML element `<comments-section>` that fetches and displays comments, and includes a form to post new ones. All client-side, no page reload.

**››› CODING TIME**

Create `web/comments-section.js` as a Lit component. It should:
- Accept a `post-slug` attribute to know which post's comments to load
- Fetch comments from `/api/comments/{postSlug}` when connected to the DOM
- Render a list of existing comments (author, date, content)
- Show a form with name and comment fields
- POST to the API on submit and refresh the list

<details>
<summary>See hint</summary>

Import `LitElement`, `html`, and `css` from `lit`. Define a class that extends `LitElement`. Use `static properties` to declare `postSlug` (attribute: `post-slug`), `serverUrl` (attribute: `server-url`), and `comments` (reactive state). In `connectedCallback()`, call `fetchComments()`. Use `${this.serverUrl}/api/comments/...` in fetch calls so the component works cross-origin. The `render()` method returns a template literal with `html\`...\``. Use `@click` for the submit button handler. After POST, set `this.comments` to the response to trigger a re-render.

</details>

<details>
<summary>See solution</summary>

Create `web/comments-section.js`:

```javascript
import { LitElement, html, css } from 'lit';

class CommentsSection extends LitElement {

  static properties = {
    postSlug: { type: String, attribute: 'post-slug' },
    serverUrl: { type: String, attribute: 'server-url' },
    comments: { state: true },
    _author: { state: true },
    _content: { state: true },
  };

  static styles = css`
    :host {
      display: block;
      font-family: inherit;
    }
    .comments-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      margin-bottom: 1.5rem;
    }
    .comment-card {
      padding: 1rem;
      border-radius: 0.5rem;
      border: 1px solid #e2e8f0;
      background: #fff;
    }
    @media (prefers-color-scheme: dark) {
      .comment-card {
        border-color: #334155;
        background: #1e293b;
      }
      .comment-meta { color: #94a3b8; }
      .comment-body { color: #cbd5e1; }
      input, textarea {
        background: #1e293b;
        border-color: #475569;
        color: #e2e8f0;
      }
    }
    .comment-meta {
      display: flex;
      justify-content: space-between;
      font-size: 0.75rem;
      color: #64748b;
      margin-bottom: 0.5rem;
    }
    .comment-meta strong {
      color: #1e293b;
    }
    @media (prefers-color-scheme: dark) {
      .comment-meta strong { color: #e2e8f0; }
    }
    .comment-body {
      font-size: 0.875rem;
      color: #334155;
    }
    .empty {
      font-size: 0.875rem;
      color: #94a3b8;
      font-style: italic;
    }
    .form {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    label {
      font-size: 0.875rem;
      font-weight: 500;
    }
    input, textarea {
      width: 100%;
      padding: 0.5rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 0.5rem;
      font-family: inherit;
      font-size: 0.875rem;
      box-sizing: border-box;
    }
    input:focus, textarea:focus {
      outline: none;
      border-color: #0ea5e9;
      box-shadow: 0 0 0 2px rgba(14, 165, 233, 0.3);
    }
    button {
      align-self: flex-start;
      padding: 0.5rem 1rem;
      border: none;
      border-radius: 0.5rem;
      background: #0284c7;
      color: white;
      font-weight: 500;
      cursor: pointer;
      font-size: 0.875rem;
    }
    button:hover { background: #0369a1; }
  `;

  constructor() {
    super();
    this.serverUrl = '';
    this.comments = [];
    this._author = '';
    this._content = '';
  }

  connectedCallback() {
    super.connectedCallback();
    this.fetchComments();
  }

  fetchComments() {
    fetch(`${this.serverUrl}/api/comments/${this.postSlug}`)
      .then(r => r.json())
      .then(data => this.comments = data);
  }

  postComment() {
    if (!this._author.trim() || !this._content.trim()) return;
    fetch(`${this.serverUrl}/api/comments`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        postSlug: this.postSlug,
        author: this._author,
        content: this._content,
      }),
    })
      .then(r => r.json())
      .then(data => {
        this.comments = data;
        this._author = '';
        this._content = '';
      });
  }

  render() {
    return html`
      <h2>Comments (${this.comments.length})</h2>

      <div class="comments-list">
        ${this.comments.length === 0
          ? html`<p class="empty">No comments yet. Be the first!</p>`
          : this.comments.map(c => html`
            <div class="comment-card">
              <div class="comment-meta">
                <strong>${c.author}</strong>
                <span>${c.createdAt}</span>
              </div>
              <div class="comment-body">${c.content}</div>
            </div>
          `)}
      </div>

      <div class="form">
        <div>
          <label>Name</label>
          <input type="text" .value=${this._author}
                 @input=${e => this._author = e.target.value}>
        </div>
        <div>
          <label>Comment</label>
          <textarea rows="3" .value=${this._content}
                    @input=${e => this._content = e.target.value}></textarea>
        </div>
        <button @click=${this.postComment}>Post comment</button>
      </div>
    `;
  }
}

customElements.define('comments-section', CommentsSection);
```

</details>

🚀🔑 This is a **web component**. It's a custom HTML element (`<comments-section>`) that encapsulates its own rendering, styles, and behavior. The styles inside `static styles` are scoped to the component (Shadow DOM), so they won't leak into the rest of your page. Lit makes reactive updates automatic: change `this.comments` and the list re-renders.

> [!NOTE]
> The file lives in `web/` because Roq's Web Bundler automatically picks up all JS files there and bundles them via esbuild. The `{#bundle /}` tag in your layout includes the bundled output. No extra configuration needed.


## 5. Embed the component in the blog

The web component is ready. Now we need to load it in the blog and use it in the post template. Since the comments app runs on port 7070, we load the bundled script cross-origin.

**››› CODING TIME**

Add a `<script>` tag loading the component from the comments server, and a `<comments-section>` element to your post layout.

<details>
<summary>See hint</summary>

If you used the default theme (Tutorial 1a), create `templates/layouts/post.html` to override the theme's post layout. Use `theme-layout: post`. If you built from scratch (Tutorial 1b), edit your existing `templates/layouts/post.html`. Add the script tag and the component after `{#insert /}`. The post slug is available as `page.slug`.

</details>

<details>
<summary>See solution (default theme)</summary>

Create `templates/layouts/post.html`:

```html
---
theme-layout: post
---

{#insert /}

<script crossorigin src="http://localhost:7070/static/bundle/main.js" type="module"></script>
<comments-section post-slug="{page.slug}" server-url="http://localhost:7070"></comments-section>
```

</details>

<details>
<summary>See solution (from-scratch theme)</summary>

In your existing `templates/layouts/post.html`, add the script and component after the article content:

```html
<script crossorigin src="http://localhost:7070/static/bundle/main.js" type="module"></script>
<comments-section post-slug="{page.slug}" server-url="http://localhost:7070"></comments-section>
```

</details>

🚀 Open the blog (on port 8080), navigate to a blog post. You should see the comments section at the bottom with a form. Try posting a comment!

🤩 The comment appears instantly, no page reload. The Lit component posted to the API, got the updated list back, and re-rendered. The blog page itself is still static HTML, served by Roq. The interactivity is entirely in the web component.


## 6. Add sample data

Let's seed some comments so you can see how the list looks with content.

**››› CODING TIME**

Create a startup observer that inserts sample comments in dev mode.

<details>
<summary>See hint</summary>

Use a CDI bean with `@Observes StartupEvent`, check `LaunchMode.DEVELOPMENT`, and persist a few comments with `postSlug` matching your existing posts.

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

🚀 Restart dev mode and navigate to your first post. The sample comments should appear in the list.


## 7. 🚀🔑 How it all fits together

Take a step back and look at what you've built:

![Architecture](https://kroki.io/d2/svg/eNplUU1PAjEQvfdXvKPG7Ee8QBpDIgZFA5GwJJy73QrFpV3bwsXw351tWRPjoe30vXnTN9NGOyWDtobD6d0-MFa3dscxpR0Z1vYLfFyOS9xYBx9E0PIW3wzorA8cfXIMMd8sFwQHQeIHaY9HZYLPfCpeTIjy0umONOmEdxJHoU1-8OzC2CDheLpGeD61LT0pP7HU0lmv3FlLBT4qR2X0IDrNsZ5VGzyuXiMC7BQ98TLboCC2GKpGKllevVf_yQutpiZSGCH3CneY3xNUn0zTKo6tqjGNsSP0QCavzmlECx1AlTprqNavBtkk5qWbT2Z7sKn7bvu55ddBEDg4yXtJa0Xj0TfsM0ufok1Kp9H-yY3df6gg90X8gbMWeKvYD6-WlXI=)

- **Roq** generates static HTML pages with a `<comments-section>` custom element tag
- The blog loads the **bundled Lit component** from the comments server via a `<script>` tag
- **The browser** sees the custom element and the component takes over
- **Lit** fetches comments from the REST API (cross-origin), renders them, and handles form submission
- **Quarkus REST + Panache** serves the API and talks to H2

No hybrid mode needed. The blog stays fully static. The dynamic part is a clean separation: a REST API + a web component in a separate microservice.

In production, the blog would be generated with `roq build` and deployed as static files (GitHub Pages, Netlify, etc.), while the comments service runs as a standalone Quarkus app wherever you host it.

🤩 You've built a full-stack comment system with two apps: a static blog and a comments microservice with a reactive web component.


## What's next?

- **Style it further**: the component uses Shadow DOM, so you can freely edit the `static styles` without affecting the rest of your site
- **Add validation**: use `@NotBlank` on the REST endpoint and show errors in the component
- **Switch to PostgreSQL**: replace H2 with `quarkus-jdbc-postgresql` for production
- **Add markdown support**: use [markdown-it](https://mvnpm.org/org.mvnpm/markdown-it) to render comment content as Markdown
- **Add relative timestamps**: use [@github/relative-time-element](https://mvnpm.org/org.mvnpm.at.github/relative-time-element) to show "2 hours ago" instead of raw dates
- **Deploy**: the static pages deploy to GitHub Pages, but the API needs a server (e.g. Fly.io, Railway, or any container host)
