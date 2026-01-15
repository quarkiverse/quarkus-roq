/**
 * Utility functions for parsing and manipulating Frontmatter
 */
import { yamlParse, yamlStringify } from '../bundle.js';

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
      frontmatter = yamlParse(rawFrontmatter) || {};
      fieldTypes = inferFieldTypes(frontmatter);
    } catch (e) {
      console.error('Error parsing Frontmatter YAML:', e);
      frontmatter = {};
      fieldTypes = {};
    }
  }

  return { frontmatter, body, rawFrontmatter, fieldTypes };
}

/**
 * Infer field types from parsed frontmatter values
 * @param {Object} frontmatter - The parsed frontmatter object
 * @returns {Object} Map of field names to their types
 */
function inferFieldTypes(frontmatter) {
  const fieldTypes = {};

  for (const [key, value] of Object.entries(frontmatter)) {
    if (Array.isArray(value)) {
      fieldTypes[key] = 'array';
    } else if (typeof value === 'boolean') {
      fieldTypes[key] = 'boolean';
    } else if (typeof value === 'number') {
      fieldTypes[key] = 'number';
    } else if (typeof value === 'string' && value.includes('\n')) {
      fieldTypes[key] = 'textarea';
    } else {
      fieldTypes[key] = 'text';
    }
  }

  return fieldTypes;
}

/**
 * Combines Frontmatter and body content
 * @param {Object} frontmatter - The frontmatter object
 * @param {string} body - The body content
 */
export function combineFrontmatter(frontmatter, body) {
  if (!frontmatter || Object.keys(frontmatter).length === 0) {
    return body || '';
  }

  const yaml = yamlStringify(frontmatter, {
    lineWidth: 0, // Disable line wrapping
  });

  return `---\n${yaml}---\n${body || ''}`;
}
