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

}

