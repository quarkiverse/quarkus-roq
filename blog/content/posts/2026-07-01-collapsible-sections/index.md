---
title: "Collapsible Sections: Hide and Reveal Content in Your Posts"
description: The Roq default theme now styles HTML collapsible sections out of the box, in both Markdown and AsciiDoc content. Perfect for tutorials with hints, FAQs, and long reference sections.
image: https://images.unsplash.com/photo-1563261438-73168c06ae56?q=80&w=1200&auto=format&fit=crop
tags: new-feature, cool-stuff
---

The Roq default theme now includes styled **collapsible sections** using the standard HTML `<details>` and `<summary>` elements. They work in both Markdown and AsciiDoc content, with a pill-shaped toggle that expands on open, an animated arrow, and a fade-in effect.

## In Markdown

Use standard HTML `<details>` and `<summary>` tags directly in your `.md` files:

```markdown
<details>
<summary>Click to reveal</summary>

Your hidden content here. **Markdown formatting** works inside.

</details>
```

Here is how it looks:

<details>
<summary>Click to reveal</summary>

Your hidden content here. **Markdown formatting** works inside.

</details>

## Tutorial hints and solutions

Collapsible sections are a great fit for tutorials where you want to give readers a chance to try on their own before revealing the answer:

<details>
<summary>Hint</summary>

Use the `@Path` and `@GET` annotations on a resource class. Return a plain `String`.

</details>

<details>
<summary>Solution</summary>

```java
@Path("/hello")
public class GreetingResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello from Roq!";
    }
}
```

</details>

## Step-by-step instructions

Multiple consecutive collapsible sections stack with a small gap between them:

<details>
<summary>Step 1: Create the project</summary>

```bash
quarkus create app my-app
```

</details>

<details>
<summary>Step 2: Add an extension</summary>

```bash
quarkus ext add rest-jackson
```

</details>

<details>
<summary>Step 3: Start dev mode</summary>

```bash
quarkus dev
```

Open `http://localhost:8080/hello` to see your endpoint.

</details>

## In AsciiDoc

AsciiDoc content has its own collapsible styling using the `%collapsible` option on an example block:

```asciidoc
.Click to expand
[%collapsible]
====
Hidden content here.
====
```

Add the `.result` role for a distinct output/result look:

```asciidoc
.Show result
[%collapsible.result]
====
Result content with a background.
====
```

Both Markdown and AsciiDoc collapsible sections are styled by the default theme with no extra configuration needed.

See them in action on the [Markdown markup test](/markups/markdown/#collapsible-sections) and [AsciiDoc markup test](/markups/asciidoc/#_collapsible_blocks) pages.
