const MARKUP_EXTENSIONS = {
    asciidoc: 'adoc',
    markdown: 'md',
    html: 'html'
};

export function markupExtension(markup) {
    return MARKUP_EXTENSIONS[markup] ?? markup;
}

/**
 * Same slug as the one computed on the server side when creating a page.
 */
export function slugify(title) {
    return (title ?? '')
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9_]+/g, '-')
        .replace(/^-+|-+$/g, '');
}

/**
 * The generated path of a new page (relative to the content directory):
 * `:slug/index.ext` for a directory page, `:slug.ext` for a file page.
 */
export function defaultPagePath(title, markup, dirPage) {
    const slug = slugify(title);
    if (!slug) {
        return '';
    }
    const extension = markupExtension(markup);
    return dirPage ? `${slug}/index.${extension}` : `${slug}.${extension}`;
}

/**
 * Converts a page path between the directory form (`foo/index.ext`) and the file form (`foo.ext`).
 * The path is returned untouched when it can't be converted.
 */
export function togglePagePathKind(path, markup, dirPage) {
    const value = (path ?? '').trim();
    const extension = markupExtension(markup);
    const indexSuffix = `/index.${extension}`;
    const fileSuffix = `.${extension}`;
    if (dirPage) {
        if (value.endsWith(indexSuffix) || value === `index.${extension}` || !value.endsWith(fileSuffix)) {
            return value;
        }
        return `${value.slice(0, -fileSuffix.length)}${indexSuffix}`;
    }
    if (!value.endsWith(indexSuffix)) {
        return value;
    }
    return `${value.slice(0, -indexSuffix.length)}${fileSuffix}`;
}

export function replaceExtension(path, markup, newMarkup) {
    const value = (path ?? '').trim();
    const suffix = `.${markupExtension(markup)}`;
    if (!value.endsWith(suffix)) {
        return value;
    }
    return `${value.slice(0, -suffix.length)}.${markupExtension(newMarkup)}`;
}
