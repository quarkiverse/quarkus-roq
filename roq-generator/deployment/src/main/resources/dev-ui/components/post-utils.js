/**
 * Utility functions for extracting post data from path information
 */
export class PostUtils {
    
    static extractPostTitle(post) {
        // Try to extract title from path or use path as fallback
        const path = post.path || '';
        const parts = path.split('/').filter(p => p);
        const fileName = parts[parts.length - 1] || path;
        // Remove extension and date prefix if present
        const title = fileName
            .replace(/\.(md|html|adoc)$/i, '')
            .replace(/^\d{4}-\d{2}-\d{2}-/, '')
            .replace(/-/g, ' ')
            .replace(/\b\w/g, l => l.toUpperCase());
        return title || 'Untitled Post';
    }

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

