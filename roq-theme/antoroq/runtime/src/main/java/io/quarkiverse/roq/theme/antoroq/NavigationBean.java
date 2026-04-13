package io.quarkiverse.roq.theme.antoroq;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;

@Named
@ApplicationScoped
public class NavigationBean {

    private volatile NavigationBuilder builder;

    private NavigationBuilder getBuilder(Site site) {
        if (builder == null) {
            synchronized (this) {
                if (builder == null) {
                    builder = new NavigationBuilder(site);
                }
            }
        }
        return builder;
    }

    public List<NavigationItem> getNavigation(Site site, Page page) {
        return getBuilder(site).getNavigationForPage(page);
    }

    public List<NavigationItem> getRootItems(Site site) {
        return getBuilder(site).getRootItems();
    }

    public NavigationItem getCurrentPage(Site site, Page page) {
        return getBuilder(site).findCurrentPage(page.url().path().toString());
    }

    public List<NavigationItem> getBreadcrumbs(Site site, Page page) {
        return getBuilder(site).getBreadcrumbs(page);
    }

    public NavigationItem getPreviousPage(Site site, Page page) {
        return getBuilder(site).getPreviousPage(page);
    }

    public NavigationItem getNextPage(Site site, Page page) {
        return getBuilder(site).getNextPage(page);
    }

    public NavigationItem getPreviousSibling(Site site, Page page) {
        NavigationItem current = getBuilder(site).findCurrentPage(page.url().path().toString());
        if (current == null) {
            return null;
        }
        return current.previousSibling();
    }

    public NavigationItem getNextSibling(Site site, Page page) {
        NavigationItem current = getBuilder(site).findCurrentPage(page.url().path().toString());
        if (current == null) {
            return null;
        }
        return current.nextSibling();
    }
}
