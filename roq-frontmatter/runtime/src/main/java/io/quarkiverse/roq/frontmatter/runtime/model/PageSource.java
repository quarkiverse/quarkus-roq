package io.quarkiverse.roq.frontmatter.runtime.model;

import java.time.ZonedDateTime;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.qute.TemplateData;

/**
 * Represent a Page or Document source
 *
 * @param template the template Source
 * @param draft
 * @param dateString
 * @param files
 * @param generated this page has been generated
 */
@TemplateData
@Vetoed
public record PageSource(
        TemplateSource template,
        boolean draft,
        String dateString,
        PageFiles files,
        boolean generated) {

    public PageSource generated(String newId) {
        return new PageSource(template().changeId(newId), draft, dateString, isSiteIndex() ? null : files, true);
    }

    public String id() {
        return template.id();
    }

    public String path() {
        return template.path();
    }

    public SourceFile file() {
        return template.file();
    }

    public String markup() {
        return template.markup();
    }

    public String fileName() {
        return template.fileName();
    }

    public String baseFileName() {
        return template.baseFileName();
    }

    public String extension() {
        return template.extension();
    }

    public boolean isSiteIndex() {
        return template.isSiteIndex();
    }

    public boolean isIndex() {
        return template.isIndex();
    }

    public boolean isTargetHtml() {
        return template.isTargetHtml();
    }

    public boolean hasFiles() {
        return files != null && !files.isEmpty();
    }

    public boolean hasNoFiles() {
        return files == null || files.isEmpty();
    }

    public boolean fileExists(Object name) {
        if (name == null) {
            return false;
        }
        if (hasNoFiles()) {
            return false;
        }
        return files().contains(name);
    }

    public ZonedDateTime date() {
        return dateString != null ? ZonedDateTime.parse(dateString) : null;
    }

    public boolean usePublicFiles() {
        return isSiteIndex() || hasNoFiles();
    }
}
