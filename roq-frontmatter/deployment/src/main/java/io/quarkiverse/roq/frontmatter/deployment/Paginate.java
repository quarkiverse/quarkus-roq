package io.quarkiverse.roq.frontmatter.deployment;

public record Paginate(int size, String link, String collection) {
    public Paginate {
        if (size < 1) {
            throw new IllegalArgumentException("Paginate size cannot be lower than 1");
        }
        if (link == null) {
            throw new IllegalArgumentException("Paginate link cannot be null");
        }
        if (collection == null) {
            throw new IllegalArgumentException("Paginate collection cannot be null");
        }
    }
}
