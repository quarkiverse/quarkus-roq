package io.quarkiverse.roq.frontmatter.runtime.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RoqUrlTest {

    private RootUrl testRoot() {
        return new RootUrl("https://example.com", "/blog");
    }

    @Test
    void testStartsWith() {
        RoqUrl url = new RoqUrl(testRoot(), "/version/3.0/guides/getting-started");
        assertTrue(url.startsWith("/blog/version/"));
        assertTrue(url.startsWith("/blog/"));
        assertFalse(url.startsWith("/docs/"));
    }

    @Test
    void testStartsWithRelativePath() {
        RoqUrl url = new RoqUrl(testRoot(), "/posts/my-post");
        assertTrue(url.startsWith("/blog/posts"));
        assertFalse(url.startsWith("/posts")); // Root path is prepended
    }

    @Test
    void testContains() {
        RoqUrl url = new RoqUrl(testRoot(), "/version/3.0/guides/getting-started");
        assertTrue(url.contains("/guides/"));
        assertTrue(url.contains("/blog/version/"));
        assertFalse(url.contains("/docs/"));
    }

    @Test
    void testContainsSubstring() {
        RoqUrl url = new RoqUrl(testRoot(), "/posts/my-post");
        assertTrue(url.contains("/blog/"));
        assertTrue(url.contains("my-post"));
        assertFalse(url.contains("/version/"));
    }

    @Test
    void testReplaceAll() {
        // Operates on resource path only (without root)
        RoqUrl url = new RoqUrl(testRoot(), "/version/3.0/guides/getting-started");
        RoqUrl result = url.replaceAll("^/version/([^/]+)/.*", "$1");
        // Result resource path is "3.0", full path is "/blog/3.0"
        assertEquals("/blog/3.0", result.path());
    }

    @Test
    void testReplaceAllRemovePrefix() {
        RoqUrl url = new RoqUrl(testRoot(), "/version/3.0/guides");
        RoqUrl result = url.replaceAll("^/version/[^/]+", "");
        assertEquals("/blog/guides", result.path());
    }

    @Test
    void testReplace() {
        RoqUrl url = new RoqUrl(testRoot(), "/version/3.15/guides");
        RoqUrl result = url.replace(".", "-");
        assertEquals("/blog/version/3-15/guides", result.path());
    }

    @Test
    void testRemoveFirst() {
        RoqUrl url = new RoqUrl(testRoot(), "/posts/my-post");
        RoqUrl result = url.removeFirst("/posts");
        assertEquals("/blog/my-post", result.path());
    }

    @Test
    void testRemoveFirstSlash() {
        RoqUrl url = new RoqUrl(testRoot(), "/posts/my-post");
        RoqUrl result = url.removeFirst("/");
        assertEquals("/blog/posts/my-post", result.path());
    }

    @Test
    void testRemoveFirstWithSpecialCharacters() {
        RoqUrl url = new RoqUrl(testRoot(), "/posts/my.post");
        RoqUrl result = url.removeFirst("/posts/my.post");
        assertEquals("/blog/", result.path());
    }
}
