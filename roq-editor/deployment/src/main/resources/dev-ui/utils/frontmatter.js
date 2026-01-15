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
 * Block scalar indicator pattern
 * Matches: |, |-, |+, >, >-, >+, and variants with explicit indentation like |2, >2-
 * Groups: [1] = style (| or >), [2] = indentation indicator (optional digit), [3] = chomping (- or + or empty)
 */
const BLOCK_SCALAR_PATTERN = /^([|>])(\d)?([+-])?$/;

/**
 * Simple YAML parser for basic key-value pairs
 * Handles strings, numbers, booleans, arrays, nested objects, and block scalars (| and >)
 * Supports block chomping indicators: clip (default), strip (-), keep (+)
 * @returns {Object} { frontmatter: Object, fieldTypes: Object }
 */
function parseYAML(yaml) {
    const result = {};
    const fieldTypes = {};
    const lines = yaml.split('\n');
    let arrayKey = null;
    
    // Block scalar state
    let inBlockScalar = false;
    let blockScalarKey = null;
    let blockScalarContent = [];
    let blockScalarIndent = 0;
    let blockScalarStyle = '|';   // '|' = literal, '>' = folded
    let blockScalarChomping = ''; // '' = clip, '-' = strip, '+' = keep

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const trimmed = line.trim();
        const leadingSpaces = line.length - line.trimStart().length;
        
        if (inBlockScalar) {
            if (blockScalarIndent === -1) {
                // First content line after block scalar indicator - set indentation and add content
                if (trimmed !== '') {
                    blockScalarIndent = leadingSpaces;
                    blockScalarContent.push(line.substring(blockScalarIndent));
                    continue;
                } else {
                    blockScalarContent.push('');
                    continue;
                }
            }
            
            // Check if we've reached the end of the block scalar
            // Block scalars end when indentation decreases below content level
            if (trimmed !== '' && leadingSpaces < blockScalarIndent) {
                // End of block scalar - indentation decreased
                result[blockScalarKey] = processBlockScalar(blockScalarContent, blockScalarStyle, blockScalarChomping);
                fieldTypes[blockScalarKey] = 'textarea';
                inBlockScalar = false;
                blockScalarKey = null;
                blockScalarContent = [];
                blockScalarIndent = 0;
                // Don't continue - process this line below
            } else if (trimmed === '') {
                // Empty line within block scalar - preserve it
                blockScalarContent.push('');
                continue;
            } else {
                // Continue block scalar - remove the base indentation
                // Content with proper indentation is part of the string, even if it contains colons
                blockScalarContent.push(line.substring(blockScalarIndent));
                continue;
            }
        }
        
        // Skip empty lines (unless we're in a block scalar)
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
            
            // Check if this is a block scalar (|, |-, |+, >, >-, >+, etc.)
            const blockMatch = value.match(BLOCK_SCALAR_PATTERN);
            if (blockMatch) {
                blockScalarKey = key;
                blockScalarStyle = blockMatch[1];        // '|' or '>'
                blockScalarChomping = blockMatch[3] || ''; // '-', '+', or ''
                // blockMatch[2] is explicit indentation indicator (rarely used, we auto-detect)
                blockScalarIndent = -1; // Will be set from first content line
                blockScalarContent = [];
                inBlockScalar = true;
                continue;
            }
            
            // Check if this is an array key
            if (value === '' || value === '[]') {
                result[key] = [];
                fieldTypes[key] = 'array';
                arrayKey = key;
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
            }
        }
    }
    
    // Handle block scalar at end of document
    if (inBlockScalar && blockScalarKey) {
        result[blockScalarKey] = processBlockScalar(blockScalarContent, blockScalarStyle, blockScalarChomping);
        fieldTypes[blockScalarKey] = 'textarea';
    }

    return { frontmatter: result, fieldTypes };
}

/**
 * Process block scalar content based on style and chomping
 * @param {string[]} lines - Content lines
 * @param {string} style - '|' for literal, '>' for folded
 * @param {string} chomping - '' for clip, '-' for strip, '+' for keep
 * @returns {string} Processed string
 */
function processBlockScalar(lines, style, chomping) {
    if (lines.length === 0) {
        return '';
    }
    
    let content;
    
    if (style === '>') {
        // Folded style: single newlines become spaces, multiple newlines preserved
        content = foldLines(lines);
    } else {
        // Literal style: preserve newlines as-is
        content = lines.join('\n');
    }
    
    // Apply chomping
    if (chomping === '-') {
        // Strip: remove all trailing newlines
        content = content.replace(/\n+$/, '');
    } else if (chomping === '+') {
        // Keep: preserve all trailing newlines
        // The join already added \n between lines, so trailing empty strings add trailing newlines
    } else {
        // Clip (default): single trailing newline
        content = content.replace(/\n+$/, '');
        // Note: we don't add the trailing newline since in frontmatter context it's usually not needed
    }
    
    return content;
}

/**
 * Fold lines according to YAML folded style rules
 * - Single newlines become spaces
 * - Multiple consecutive newlines are preserved (minus one)
 * - Lines that start with whitespace are not folded (literal)
 * @param {string[]} lines - Content lines
 * @returns {string} Folded content
 */
function foldLines(lines) {
    if (lines.length === 0) return '';
    if (lines.length === 1) return lines[0];
    
    const result = [];
    let i = 0;
    
    while (i < lines.length) {
        const line = lines[i];
        
        if (line === '') {
            // Empty line - preserve as newline
            result.push('\n');
            i++;
        } else if (line.startsWith(' ') || line.startsWith('\t')) {
            // Line starts with whitespace - keep literal (no folding)
            if (result.length > 0 && !result[result.length - 1].endsWith('\n')) {
                result.push('\n');
            }
            result.push(line);
            result.push('\n');
            i++;
        } else {
            // Regular line - fold with next regular lines
            let segment = line;
            i++;
            
            while (i < lines.length) {
                const nextLine = lines[i];
                if (nextLine === '' || nextLine.startsWith(' ') || nextLine.startsWith('\t')) {
                    // End of foldable segment
                    break;
                }
                // Fold: join with space
                segment += ' ' + nextLine;
                i++;
            }
            
            if (result.length > 0 && !result[result.length - 1].endsWith('\n')) {
                result.push('\n');
            }
            result.push(segment);
        }
    }
    
    return result.join('');
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
        if (value.length === 0 || /[:#|>!@%&*{}\[\]]/.test(value) || /^\s|\s$/.test(value)) {
            return `"${value.replace(/"/g, '\\"')}"`;
        }
        return value;
    }
    return String(value);
}

