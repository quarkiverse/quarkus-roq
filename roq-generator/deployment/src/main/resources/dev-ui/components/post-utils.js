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

    static extractPostDescription(post) {
        // Try to extract description from path or return null
        // In a real implementation, this would come from frontmatter data
        const path = post.path || '';
        const parts = path.split('/').filter(p => p);
        const fileName = parts[parts.length - 1] || '';
        // Try to infer description from filename
        if (fileName.includes('migration')) {
            return 'Site migration and updates';
        } else if (fileName.includes('diagram')) {
            return 'Visualization and diagram generation';
        } else if (fileName.includes('search')) {
            return 'Search capabilities and features';
        }
        return null;
    }

    static extractPostDate(post) {
        // Extract date from path if it follows the pattern YYYY-MM-DD
        const path = post.path || '';
        const dateMatch = path.match(/(\d{4}-\d{2}-\d{2})/);
        if (dateMatch) {
            const dateStr = dateMatch[1];
            try {
                const date = new Date(dateStr);
                const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 
                               'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
                const year = date.getFullYear();
                const month = months[date.getMonth()];
                const day = date.getDate();
                return `${year}, ${month} ${day.toString().padStart(2, '0')}`;
            } catch (e) {
                return null;
            }
        }
        return null;
    }

    static extractReadTime(post) {
        // Placeholder - in real implementation would come from post data
        // For now, return a default or calculate based on path
        return '2 minute(s) read';
    }

    static extractImageUrl(post) {
        // Try to find image in the same directory or return null
        // In a real implementation, this would come from frontmatter data
        const path = post.path || '';
        // Check if there's an image reference in the path structure
        // This is a placeholder - real implementation would check frontmatter
        return null;
    }
}

