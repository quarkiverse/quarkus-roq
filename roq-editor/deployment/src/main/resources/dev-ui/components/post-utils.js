/**
 * Utility functions for extracting post data from path information
 */
export class PostUtils {
    

    static extractFileType(post) {
        // Extract file extension from path
        const path = post.path || '';
        const match = path.match(/\.([^.]+)$/);
        if (match) {
            const ext = match[1].toLowerCase();
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
}

