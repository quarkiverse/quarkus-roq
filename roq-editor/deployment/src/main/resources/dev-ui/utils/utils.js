const QUTE_SECTION_RE = /(?<!\\)\{#([^\s}]+)}/;

export function containsQuteSection(content) {
    return QUTE_SECTION_RE.test(content);
}


export function containsPotentialHtml(content) {
    if (typeof content !== 'string') return false;
    // Common structural + formatting tags that matter for editing
    const htmlLike = /<\/?[a-z][\w-]*(\s+[^>]*)?>/i;
    // Remove fenced code blocks ```...```
    let clean = content.replace(/```[\s\S]*?```/g, "");
    // Remove inline code `...`
    clean = content.replace(/`[^`]*`/g, "");
    return htmlLike.test(clean);
}

export function containsDataRawTag(content) {
    return /<[^>]+\bdata-raw\b[^>]*>/.test(content);
}