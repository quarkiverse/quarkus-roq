/**
 * Utility functions for extracting post data from path information
 */
export class PostUtils {

    static getFileExtension(path) {
        const match = path.match(/\.([^.]+)$/);
        if (match) {
            return match[1].toLowerCase();
        }
        return null;
    }

    static extractFileType(post) {
        // Extract file extension from path
        const path = post.path || '';
        const ext = this.getFileExtension(path);
        if (ext) {
            // Map common extensions
            const extMap = {
                'md': 'Markdown',
                'adoc': 'AsciiDoc',
                'html': 'HTML',
                'htm': 'HTML'
            };
            return extMap[ext] || ext.toUpperCase();
        }
        return null;
    }

    /**
     * Slugify a string to create URL-friendly slugs
     * Matches the Java PathUtils.slugify logic
     */
    static slugify(value) {
        if (!value) {
            return '';
        }
        return value
            .toLowerCase()
            .trim()
            .replace(/[^a-z0-9_\-]/g, '-') // Replace non-alphanumeric characters with hyphens
            .replace(/-+/g, '-') // Replace multiple hyphens with a single one
            .replace(/^-|-$/g, ''); // Remove leading/trailing hyphens
    }

    /**
     * Generate preview URL from frontmatter title
     * Uses the slug field if available, otherwise slugifies the title
     * For posts, the URL format is: posts/{slug}/
     */
    static generatePreviewUrl(frontmatter, filePath) {
        if (!frontmatter || !filePath) {
            return null;
        }

        // Only generate preview URLs for posts
        if (!filePath.startsWith('posts/')) {
            return null;
        }

        // Get slug from frontmatter, or slugify the title
        const slug = frontmatter.slug || (frontmatter.title ? this.slugify(frontmatter.title) : null);
        
        if (!slug) {
            return null;
        }

        return `/posts/${slug}/`;
    }
}

