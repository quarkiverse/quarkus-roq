/**
 * Utility functions for parsing and manipulating Frontmatter
 */

// Pattern matches: ^---\v.*?---(?:\v|$)
// Where \v matches vertical whitespace (newlines, carriage returns, etc.)
const FRONTMATTER_PATTERN = /^---[\r\n\u000B\u000C\u0085\u2028\u2029]+(.*?)[\r\n\u000B\u000C\u0085\u2028\u2029]+---(?:[\r\n\u000B\u000C\u0085\u2028\u2029]|$)(.*)$/s;

/**
 * Checks if content has Frontmatter
 */
export function hasFrontmatter(content) {
    if (!content || typeof content !== 'string') {
        return false;
    }
    return FRONTMATTER_PATTERN.test(content);
}

/**
 * Extracts Frontmatter YAML and body content from a file
 * @returns {Object} { frontmatter: Object, body: string, rawFrontmatter: string, fieldTypes: Object }
 */
export function parseFrontmatter(content) {
    if (!content || typeof content !== 'string') {
        return { frontmatter: {}, body: content || '', rawFrontmatter: '', fieldTypes: {} };
    }

    const match = content.match(FRONTMATTER_PATTERN);
    if (!match) {
        return { frontmatter: {}, body: content, rawFrontmatter: '', fieldTypes: {} };
    }

    const rawFrontmatter = match[1].trim();
    const body = match[2] || '';

    let frontmatter = {};
    let fieldTypes = {};
    if (rawFrontmatter) {
        try {
            const parseResult = parseYAML(rawFrontmatter);
            frontmatter = parseResult.frontmatter;
            fieldTypes = parseResult.fieldTypes;
        } catch (e) {
            console.error('Error parsing Frontmatter YAML:', e);
            frontmatter = {};
            fieldTypes = {};
        }
    }

    return { frontmatter, body, rawFrontmatter, fieldTypes };
}

/**
 * Combines Frontmatter and body content
 * @param {Object} frontmatter - The frontmatter object
 * @param {string} body - The body content
 * @param {Object} fieldTypes - Optional map of field types: { fieldName: 'textarea' | 'text' | ... }
 */
export function combineFrontmatter(frontmatter, body, fieldTypes = {}) {
    if (!frontmatter || Object.keys(frontmatter).length === 0) {
        return body || '';
    }

    const yaml = stringifyYAML(frontmatter, 0, fieldTypes);
    return `---\n${yaml}\n---\n${body || ''}`;
}

/**
 * Simple YAML parser for basic key-value pairs
 * Handles strings, numbers, booleans, arrays, nested objects, and multi-line strings with |
 * @returns {Object} { frontmatter: Object, fieldTypes: Object }
 */
function parseYAML(yaml) {
    const result = {};
    const fieldTypes = {};
    const lines = yaml.split('\n');
    let arrayKey = null;
    let inMultilineString = false;
    let multilineKey = null;
    let multilineContent = [];
    let multilineIndent = 0;

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const trimmed = line.trim();
        const leadingSpaces = line.length - line.trimStart().length;
        
        if (inMultilineString) {
            if (multilineIndent === -1) {
                if (trimmed !== '') {
                    multilineIndent = leadingSpaces;
                } else {
                    multilineContent.push('');
                    continue;
                }
            }
            
            // Check if we've reached the end of the multi-line string
            // End conditions:
            // 1. Non-empty line that starts a new key-value pair (contains ':' not at start/end)
            // 2. Non-empty line with less indentation than the content
            const isNewKeyValue = trimmed !== '' && trimmed.includes(':') && 
                                  trimmed.indexOf(':') > 0 && 
                                  trimmed.indexOf(':') < trimmed.length - 1 &&
                                  !trimmed.startsWith('- '); // Not an array item
            
            if (isNewKeyValue) {
                // End of multi-line string - new key-value pair detected
                result[multilineKey] = multilineContent.join('\n');
                fieldTypes[multilineKey] = 'textarea';
                inMultilineString = false;
                multilineKey = null;
                multilineContent = [];
                multilineIndent = 0;
                // Don't continue - process this line as a new key-value pair below
            } else if (trimmed !== '' && leadingSpaces < multilineIndent) {
                // End of multi-line string - indentation decreased
                result[multilineKey] = multilineContent.join('\n');
                fieldTypes[multilineKey] = 'textarea';
                inMultilineString = false;
                multilineKey = null;
                multilineContent = [];
                multilineIndent = 0;
                // Don't continue - process this line below
            } else if (trimmed === '') {
                // Empty line within multi-line string - preserve it
                multilineContent.push('');
                continue;
            } else if (leadingSpaces >= multilineIndent) {
                // Continue multi-line string - remove the base indentation
                multilineContent.push(line.substring(multilineIndent));
                continue;
            }
        }
        
        // Skip empty lines (unless we're in a multi-line string)
        if (!trimmed) {
            continue;
        }

        // Check for array item
        if (trimmed.startsWith('- ')) {
            const arrayValue = trimmed.substring(2).trim();
            if (arrayKey) {
                if (!result[arrayKey]) {
                    result[arrayKey] = [];
                }
                result[arrayKey].push(parseValue(arrayValue));
            }
            continue;
        }

        // Check for key-value pair
        const colonIndex = line.indexOf(':');
        if (colonIndex > 0) {
            const key = line.substring(0, colonIndex).trim();
            const value = line.substring(colonIndex + 1).trim();
            
            // Check if this is a multi-line string (key: |)
            if (value === '|' || value === '|+') {
                multilineKey = key;
                // Content lines after | are typically indented 2 spaces relative to the key start
                // We'll detect the actual indentation from the next non-empty line
                multilineIndent = -1; // Will be set from first content line
                multilineContent = [];
                inMultilineString = true;
                continue;
            }
            
            // Check if this is an array key
            if (value === '' || value === '[]') {
                result[key] = [];
                fieldTypes[key] = 'array';
                arrayKey = key;
                inArray = true;
            } else {
                const parsedValue = parseValue(value);
                result[key] = parsedValue;
                // Infer field type
                if (typeof parsedValue === 'boolean') {
                    fieldTypes[key] = 'boolean';
                } else if (typeof parsedValue === 'number') {
                    fieldTypes[key] = 'number';
                } else {
                    fieldTypes[key] = 'text';
                }
                arrayKey = null;
                inArray = false;
            }
        }
    }
    
    if (inMultilineString && multilineKey) {
        result[multilineKey] = multilineContent.join('\n');
        fieldTypes[multilineKey] = 'textarea';
    }

    return { frontmatter: result, fieldTypes };
}

/**
 * Parse a YAML value (string, number, boolean, null, array)
 */
function parseValue(value) {
    const trimmed = value.trim();
    
    if (trimmed === 'true') return true;
    if (trimmed === 'false') return false;
    if (trimmed === 'null' || trimmed === '~' || trimmed === '') return null;
    
    if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
        const arrayContent = trimmed.slice(1, -1).trim();
        if (!arrayContent) {
            return [];
        }
        return arrayContent.split(',').map(item => parseValue(item.trim()));
    }
    
    // Try to parse as number
    if (/^-?\d+$/.test(trimmed)) {
        return parseInt(trimmed, 10);
    }
    if (/^-?\d*\.\d+$/.test(trimmed)) {
        return parseFloat(trimmed);
    }
    
    // Remove quotes if present
    if ((trimmed.startsWith('"') && trimmed.endsWith('"')) ||
        (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
        return trimmed.slice(1, -1);
    }
    
    return trimmed;
}

/**
 * Simple YAML stringifier
 * @param {Object} obj - The object to stringify
 * @param {number} indent - Current indentation level
 * @param {Object} fieldTypes - Map of field types: { fieldName: 'textarea' | 'text' | ... }
 */
function stringifyYAML(obj, indent = 0, fieldTypes = {}) {
    const indentStr = '  '.repeat(indent);
    const lines = [];

    for (const [key, value] of Object.entries(obj)) {
        const fieldType = fieldTypes[key];
        
        if (value === null || value === undefined) {
            lines.push(`${indentStr}${key}: null`);
        } else if (Array.isArray(value)) {
            if (value.length === 0) {
                lines.push(`${indentStr}${key}: []`);
            } else {
                lines.push(`${indentStr}${key}:`);
                for (const item of value) {
                    if (typeof item === 'object' && item !== null) {
                        lines.push(`${indentStr}  - ${stringifyYAML(item, indent + 1, {})}`);
                    } else {
                        lines.push(`${indentStr}  - ${stringifyValue(item)}`);
                    }
                }
            }
        } else if (typeof value === 'object') {
            lines.push(`${indentStr}${key}:`);
            lines.push(stringifyYAML(value, indent + 1, {}));
        } else if (fieldType === 'textarea' && typeof value === 'string') {
            // Use | syntax for textarea fields
            lines.push(`${indentStr}${key}: |`);
            const multilineValue = String(value);
            if (multilineValue) {
                const multilineLines = multilineValue.split('\n');
                for (const line of multilineLines) {
                    lines.push(`${indentStr}  ${line}`);
                }
            }
        } else {
            lines.push(`${indentStr}${key}: ${stringifyValue(value)}`);
        }
    }

    return lines.join('\n');
}

/**
 * Stringify a single value
 */
function stringifyValue(value) {
    if (value === null || value === undefined) {
        return 'null';
    }
    if (typeof value === 'boolean') {
        return value.toString();
    }
    if (typeof value === 'number') {
        return value.toString();
    }
    if (typeof value === 'string') {
        // Quote if contains special characters or starts/ends with whitespace
        if (/[:#|>!@%&*{}\[\]]/.test(value) || /^\s|\s$/.test(value)) {
            return `"${value.replace(/"/g, '\\"')}"`;
        }
        return value;
    }
    return String(value);
}

