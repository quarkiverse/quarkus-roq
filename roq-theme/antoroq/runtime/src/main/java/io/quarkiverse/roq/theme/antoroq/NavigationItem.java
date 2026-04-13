package io.quarkiverse.roq.theme.antoroq;

import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkus.qute.TemplateData;
import io.vertx.core.json.JsonObject;

@TemplateData
public class NavigationItem {

    private final String content;
    private final String url;
    private final String urlType;
    private final List<NavigationItem> items;
    private final int depth;
    private final NavigationItem parent;
    private final JsonObject pageData;
    private final Page page;
    private final int order;
    private final String navTitle;
    private final boolean hidden;
    private final boolean external;
    private final String externalUrl;

    public NavigationItem(String content, String url, String urlType, int depth, NavigationItem parent,
            JsonObject pageData) {
        this(content, url, urlType, depth, parent, pageData, null, 0, null, false, false, null);
    }

    public NavigationItem(String content, String url, String urlType, int depth, NavigationItem parent,
            JsonObject pageData, Page page, int order, String navTitle, boolean hidden, boolean external,
            String externalUrl) {
        this.content = content;
        this.url = url;
        this.urlType = urlType;
        this.depth = depth;
        this.parent = parent;
        this.pageData = pageData;
        this.page = page;
        this.order = order;
        this.navTitle = navTitle != null ? navTitle : content;
        this.hidden = hidden;
        this.external = external;
        this.externalUrl = externalUrl;
        this.items = new ArrayList<>();
    }

    public String getContent() {
        return content;
    }

    public String getUrl() {
        return url;
    }

    public String getUrlType() {
        return urlType;
    }

    public List<NavigationItem> getItems() {
        return items;
    }

    public int getDepth() {
        return depth;
    }

    public NavigationItem getParent() {
        return parent;
    }

    public JsonObject getPageData() {
        return pageData;
    }

    public Page getPage() {
        return page;
    }

    public int getOrder() {
        return order;
    }

    public String getNavTitle() {
        return navTitle;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isExternal() {
        return external;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    public NavigationItem addItem(NavigationItem item) {
        this.items.add(item);
        return this;
    }

    public List<NavigationItem> getBreadcrumbs() {
        List<NavigationItem> crumbs = new ArrayList<>();
        NavigationItem current = this;
        while (current != null) {
            crumbs.add(0, current);
            current = current.getParent();
        }
        return crumbs;
    }

    public NavigationItem previousSibling() {
        if (parent == null) {
            return null;
        }
        List<NavigationItem> siblings = parent.getItems();
        int idx = siblings.indexOf(this);
        if (idx > 0) {
            return siblings.get(idx - 1);
        }
        return null;
    }

    public NavigationItem nextSibling() {
        if (parent == null) {
            return null;
        }
        List<NavigationItem> siblings = parent.getItems();
        int idx = siblings.indexOf(this);
        if (idx >= 0 && idx < siblings.size() - 1) {
            return siblings.get(idx + 1);
        }
        return null;
    }

    public String render(String currentUrl, int depth) {
        StringBuilder sb = new StringBuilder();
        renderRecursive(sb, currentUrl, depth);
        return sb.toString();
    }

    private void renderRecursive(StringBuilder sb, String currentUrl, int depth) {
        if (items == null || items.isEmpty()) {
            return;
        }
        sb.append("<ul class=\"nav-list\">");
        for (NavigationItem item : items) {
            if (item.hidden) {
                continue;
            }
            boolean isCurrent = currentUrl != null && currentUrl.equals(item.url);
            boolean hasChildren = item.items != null && !item.items.isEmpty();

            sb.append("<li class=\"nav-item");
            if (isCurrent) {
                sb.append(" is-current-page");
            }
            if (hasChildren) {
                sb.append(" has-children");
            }
            sb.append("\" data-depth=\"").append(depth).append("\">");

            if (item.navTitle != null && !item.navTitle.isEmpty()) {
                if (hasChildren) {
                    sb.append("<button class=\"nav-item-toggle\" aria-label=\"Toggle expand/collapse\"></button>");
                }
                if (item.external && item.externalUrl != null) {
                    sb.append("<a class=\"nav-link\" href=\"").append(escapeHtml(item.externalUrl))
                            .append("\" target=\"_blank\" rel=\"noopener\">");
                    sb.append(escapeHtml(item.navTitle));
                    sb.append("</a>");
                } else if (item.url != null && !item.url.isEmpty() && !item.url.equals("#")) {
                    sb.append("<a class=\"nav-link\" href=\"").append(escapeHtml(item.url)).append("\">");
                    sb.append(escapeHtml(item.navTitle));
                    sb.append("</a>");
                } else {
                    sb.append("<span class=\"nav-text\">").append(escapeHtml(item.navTitle)).append("</span>");
                }
            }

            if (hasChildren) {
                item.renderRecursive(sb, currentUrl, depth + 1);
            }

            sb.append("</li>");
        }
        sb.append("</ul>");
    }

    private static String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public String renderFlat(String currentUrl) {
        StringBuilder sb = new StringBuilder();
        renderFlatRecursive(sb, currentUrl, 0);
        return sb.toString();
    }

    private void renderFlatRecursive(StringBuilder sb, String currentUrl, int depth) {
        if (items == null)
            return;
        for (NavigationItem item : items) {
            if (item.hidden) {
                continue;
            }
            boolean isCurrent = currentUrl != null && currentUrl.equals(item.url);
            sb.append("<li class=\"nav-item");
            if (isCurrent)
                sb.append(" is-current-page");
            if (item.items != null && !item.items.isEmpty())
                sb.append(" has-children");
            sb.append("\" data-depth=\"").append(depth).append("\" data-url=\"")
                    .append(item.url != null ? escapeHtml(item.url) : "").append("\">");

            if (item.navTitle != null && !item.navTitle.isEmpty()) {
                if (item.external && item.externalUrl != null) {
                    sb.append("<a class=\"nav-link\" href=\"").append(escapeHtml(item.externalUrl))
                            .append("\" target=\"_blank\" rel=\"noopener\">")
                            .append(escapeHtml(item.navTitle)).append("</a>");
                } else if (item.url != null && !item.url.isEmpty() && !item.url.equals("#")) {
                    sb.append("<a class=\"nav-link\" href=\"").append(escapeHtml(item.url)).append("\">")
                            .append(escapeHtml(item.navTitle)).append("</a>");
                } else {
                    sb.append("<span class=\"nav-text\">").append(escapeHtml(item.navTitle)).append("</span>");
                }
            }

            if (item.items != null && !item.items.isEmpty()) {
                item.renderFlatRecursive(sb, currentUrl, depth + 1);
            }
            sb.append("</li>");
        }
    }
}
