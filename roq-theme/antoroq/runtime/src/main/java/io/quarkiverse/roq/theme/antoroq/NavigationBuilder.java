package io.quarkiverse.roq.theme.antoroq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.vertx.core.json.JsonObject;

public class NavigationBuilder {

    public static final String INDEX_URL = "/";
    private static final Pattern NUMERIC_PREFIX = Pattern.compile("^(\\d+)[-_](.+)$");

    private final Site site;
    private final List<NavigationItem> rootItems;
    private final Map<String, NavigationItem> itemsByPath;
    private final Map<String, Page> indexPagesByDir;
    private final NavigationItem root;

    public NavigationBuilder(Site site) {
        this.site = site;
        this.rootItems = new ArrayList<>();
        this.itemsByPath = new HashMap<>();
        this.indexPagesByDir = new HashMap<>();
        this.root = new NavigationItem("Root", INDEX_URL, "internal", 0, null, null);
        scanIndexPages();
        buildTree();
        sortAllItems(rootItems);
    }

    private void scanIndexPages() {
        for (Page page : site.pages()) {
            String baseFileName = page.source().baseFileName();
            if ("index".equals(baseFileName) && !page.source().isSiteIndex()) {
                String dirPath = getDirectoryPath(page.source().path());
                indexPagesByDir.put(dirPath, page);
            }
        }
    }

    public List<NavigationItem> getRootItems() {
        return rootItems;
    }

    public NavigationItem getRoot() {
        return root;
    }

    private void buildTree() {
        for (Page page : site.pages()) {
            if (page.source().isSiteIndex()) {
                continue;
            }
            if (isNavHidden(page)) {
                continue;
            }

            String path = page.source().path();
            NavigationItem item = createNavigationItem(page);
            itemsByPath.put(path, item);
            addToTree(path, item);
        }
    }

    private NavigationItem createNavigationItem(Page page) {
        String title = extractNavTitle(page);
        if (title == null || title.isEmpty()) {
            title = page.title();
        }
        if (title == null || title.isEmpty()) {
            title = page.source().baseFileName();
        }

        String url = page.url().path().toString();
        if (page.url().toString().endsWith("/") && !url.endsWith("/")) {
            url = url + "/";
        }

        String content = title.replaceAll("<[^>]*>", "");
        int order = extractNavOrder(page);
        String navTitle = content;
        boolean hidden = isNavHidden(page);
        boolean external = isExternalLink(page);
        String externalUrl = extractExternalUrl(page);

        return new NavigationItem(content, url, "internal", 0, null, page.data(), page, order, navTitle, hidden,
                external, externalUrl);
    }

    private int extractNavOrder(Page page) {
        JsonObject data = page.data();
        if (data == null) {
            return parseNumericPrefix(page.source().baseFileName());
        }

        Integer navOrder = data.getInteger("nav_order");
        if (navOrder != null) {
            return navOrder;
        }

        Integer weight = data.getInteger("weight");
        if (weight != null) {
            return weight;
        }

        return parseNumericPrefix(page.source().baseFileName());
    }

    private int parseNumericPrefix(String filename) {
        if (filename == null) {
            return Integer.MAX_VALUE;
        }
        Matcher matcher = NUMERIC_PREFIX.matcher(filename);
        if (matcher.matches()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }
        return Integer.MAX_VALUE;
    }

    private String extractNavTitle(Page page) {
        JsonObject data = page.data();
        if (data == null) {
            return null;
        }

        String navTitle = data.getString("nav_title");
        if (navTitle != null && !navTitle.isEmpty()) {
            return navTitle;
        }

        return page.title();
    }

    private boolean isNavHidden(Page page) {
        if (page.draft()) {
            return true;
        }
        JsonObject data = page.data();
        if (data == null) {
            return false;
        }
        Boolean hidden = data.getBoolean("nav_hide");
        return hidden != null && hidden;
    }

    private boolean isExternalLink(Page page) {
        JsonObject data = page.data();
        if (data == null) {
            return false;
        }
        return data.getString("nav_external") != null;
    }

    private String extractExternalUrl(Page page) {
        JsonObject data = page.data();
        if (data == null) {
            return null;
        }
        return data.getString("nav_external");
    }

    private void addToTree(String path, NavigationItem item) {
        String[] parts = path.split("/");

        if (parts.length == 1) {
            rootItems.add(item);
            return;
        }

        String parentDirPath = getDirectoryPath(path);
        Page indexPage = indexPagesByDir.get(parentDirPath);

        if (indexPage != null && !isPageOf(item, indexPage)) {
            NavigationItem parentItem = itemsByPath.get(indexPage.source().path());
            if (parentItem == null) {
                parentItem = createNavigationItem(indexPage);
                itemsByPath.put(indexPage.source().path(), parentItem);

                String grandParentDirPath = getDirectoryPath(parentDirPath);
                if (!grandParentDirPath.isEmpty()) {
                    NavigationItem grandParent = findOrCreateParentByPath(grandParentDirPath);
                    grandParent.addItem(parentItem);
                    parentItem = new NavigationItem(parentItem.getContent(), parentItem.getUrl(), parentItem.getUrlType(),
                            parentItem.getDepth() + 1, grandParent, parentItem.getPageData(), parentItem.getPage(),
                            parentItem.getOrder(), parentItem.getNavTitle(), parentItem.isHidden(),
                            parentItem.isExternal(), parentItem.getExternalUrl());
                } else {
                    rootItems.add(parentItem);
                }
            }
            parentItem.addItem(item);
        } else {
            String parentPath = getParentPath(path);
            NavigationItem parent = itemsByPath.get(parentPath);

            if (parent == null) {
                parent = findOrCreateParent(parts, 0);
            }

            parent.addItem(item);
        }
    }

    private boolean isPageOf(NavigationItem item, Page page) {
        return item.getPage() != null && item.getPage().source().path().equals(page.source().path());
    }

    private String getDirectoryPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "";
        }
        return path.substring(0, lastSlash);
    }

    private String getParentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "";
        }
        return path.substring(0, lastSlash);
    }

    private NavigationItem findOrCreateParent(String[] pathParts, int currentIndex) {
        StringBuilder currentPath = new StringBuilder();
        for (int i = 0; i <= currentIndex; i++) {
            if (i > 0)
                currentPath.append("/");
            currentPath.append(pathParts[i]);
        }
        String path = currentPath.toString();

        if (itemsByPath.containsKey(path)) {
            return itemsByPath.get(path);
        }

        NavigationItem parent = createDirectoryItem(pathParts[currentIndex], path);
        itemsByPath.put(path, parent);

        if (currentIndex == 0) {
            rootItems.add(parent);
        } else {
            NavigationItem grandParent = findOrCreateParent(pathParts, currentIndex - 1);
            grandParent.addItem(parent);
        }

        return parent;
    }

    private NavigationItem findOrCreateParentByPath(String dirPath) {
        if (dirPath == null || dirPath.isEmpty()) {
            return root;
        }

        NavigationItem existing = itemsByPath.get(dirPath);
        if (existing != null) {
            return existing;
        }

        String[] parts = dirPath.split("/");
        return findOrCreateParent(parts, parts.length - 1);
    }

    private NavigationItem createDirectoryItem(String name, String path) {
        String title = formatDirectoryName(name);
        String url = "/" + path + "/";
        return new NavigationItem(title, url, "internal", 0, null, null, null, Integer.MAX_VALUE, title, false, false,
                null);
    }

    private String formatDirectoryName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        String cleanName = name;
        Matcher matcher = NUMERIC_PREFIX.matcher(name);
        if (matcher.matches()) {
            cleanName = matcher.group(2);
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < cleanName.length(); i++) {
            char c = cleanName.charAt(i);
            if (i == 0) {
                result.append(Character.toUpperCase(c));
            } else if (c == '-' || c == '_') {
                result.append(' ');
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private void sortAllItems(List<NavigationItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        items.sort(Comparator.comparingInt(NavigationItem::getOrder)
                .thenComparing(item -> item.getNavTitle() != null ? item.getNavTitle() : ""));

        for (NavigationItem item : items) {
            sortAllItems(item.getItems());
        }
    }

    private void sortItems(List<NavigationItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        items.sort(Comparator.comparingInt(NavigationItem::getOrder)
                .thenComparing(item -> item.getNavTitle() != null ? item.getNavTitle() : ""));
    }

    public NavigationItem findCurrentPage(String currentUrl) {
        for (NavigationItem item : rootItems) {
            NavigationItem found = findInTree(item, currentUrl);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private NavigationItem findInTree(NavigationItem item, String currentUrl) {
        if (currentUrl.equals(item.getUrl()) || currentUrl.equals(item.getUrl() + "/")) {
            return item;
        }
        for (NavigationItem child : item.getItems()) {
            NavigationItem found = findInTree(child, currentUrl);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public List<NavigationItem> getNavigationForPage(Page page) {
        String currentUrl = page.url().path().toString();
        NavigationItem current = findCurrentPage(currentUrl);

        List<NavigationItem> nav = new ArrayList<>();

        if (current != null) {
            NavigationItem parent = current.getParent();
            if (parent != null && parent != root) {
                nav.addAll(parent.getItems());
            }
        }

        if (nav.isEmpty()) {
            nav.addAll(rootItems);
        }

        return nav;
    }

    public List<NavigationItem> getBreadcrumbs(Page page) {
        NavigationItem current = findCurrentPage(page.url().path().toString());
        if (current == null) {
            return new ArrayList<>();
        }
        return current.getBreadcrumbs();
    }

    public NavigationItem getPreviousPage(Page page) {
        NavigationItem current = findCurrentPage(page.url().path().toString());
        if (current == null) {
            return null;
        }
        return current.previousSibling();
    }

    public NavigationItem getNextPage(Page page) {
        NavigationItem current = findCurrentPage(page.url().path().toString());
        if (current == null) {
            return null;
        }
        return current.nextSibling();
    }
}
