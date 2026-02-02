---
title: "Roq n Roll Your Tests 🎶"
description: Testing the actual Roq generation has never been this cool! 🎸  
image: "c'est de la poussière d'étoile.jpg"
tags: cool-stuff
author: ia3andy
---

Hello folks,

I'm excited to share something very cool! I've developed a way to:
- Test the **full generation** of your website.
- Use **RestAssured** to test the generated site (thanks to an already started static server).

### Step 1: Add the Dependency

First, include the `quarkus-roq-testing` test dependency in your `pom.xml`.

### Step 2: Basic Test Example

Once you've added the dependency, you can easily ensure all pages are generated without errors:

```java
@QuarkusTest
@RoqAndRoll
public class RoqSiteTest {
    
    @Test
    public void testGen() {
        // All pages will be generated/validated during test setup
    }
}
```

That's it! This basic test already verifies that your site generation is error-free.

---

### Step 3: Test the Generated Content

To go even further, you can test the actual content of your generated site. The RestAssured port will automatically use the Roq static server. Here's how:

```java
@QuarkusTest
@RoqAndRoll
public class RoqSiteTest {

    @Test
    public void testIndex() {
        RestAssured.when().get("/")
                .then()
                .statusCode(200)
                .body(containsString(
                    "Roq is a static site generator that makes it easy to build websites and blogs"
                ));
    }
}
```

---

### Why I Love It ❤️

With just a few annotations and a bit of setup, you can effortlessly test both the generation process and the content of your site. It's powerful, elegant, and super simple to use.

Give it a try and let me know how it works for you. Happy testing! 🚀
