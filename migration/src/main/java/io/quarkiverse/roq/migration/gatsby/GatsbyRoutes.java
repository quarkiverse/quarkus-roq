package io.quarkiverse.roq.migration.gatsby;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.roq.migration.SiteFiles;

/**
 * The components of a Gatsby site that only its File System Route pages use, such as
 * {@code src/pages/{markdownRemark.fields__slug}.js}: those pages render the Markdown pages, which Roq does with its page
 * layout, so nothing needs what only they use.
 */
final class GatsbyRoutes {

    static final List<String> EXTENSIONS = List.of(".js", ".jsx", ".tsx", ".ts");
    static final Pattern LOCAL_IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:[^\"';]+?\\s+from\\s+)?[\"'](\\.[^\"']*)[\"']");

    private GatsbyRoutes() {
    }

    /**
     * @param siteDir the site being converted
     * @return the components only the File System Route pages use, directly or through each other, relative to the
     *         site, with the names of those pages, e.g. {@code {markdownRemark.fields__slug}.jsx}
     */
    static Map<String, Set<String>> replacedByRoq(GatsbyProject project, Path siteDir) throws IOException {
        List<String> files = new ArrayList<>();
        for (Path file : SiteFiles.files(siteDir.resolve(project.path("src")), GatsbyProject::isReactSource)) {
            files.add(SiteFiles.relativePath(siteDir, file));
        }
        Map<String, Set<String>> importers = new LinkedHashMap<>();
        for (String file : files) {
            Matcher imported = LOCAL_IMPORT.matcher(Files.readString(siteDir.resolve(file)));
            while (imported.find()) {
                String target = resolve(file, imported.group(1), files);
                if (target != null && !target.equals(file)) {
                    importers.computeIfAbsent(target, t -> new TreeSet<>()).add(file);
                }
            }
        }
        Map<String, Set<String>> replaced = new LinkedHashMap<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, Set<String>> users : importers.entrySet()) {
                String file = users.getKey();
                if (replaced.containsKey(file) || file.startsWith(project.path("src/pages/"))
                        || !users.getValue().stream().allMatch(u -> isRoute(project, u) || replaced.containsKey(u))) {
                    continue;
                }
                Set<String> routes = new TreeSet<>();
                users.getValue().forEach(u -> routes.addAll(isRoute(project, u) ? Set.of(name(u)) : replaced.get(u)));
                replaced.put(file, routes);
                changed = true;
            }
        }
        return replaced;
    }

    /**
     * @return true for a File System Route page, such as {@code {markdownRemark.fields__slug}.jsx}
     */
    static boolean isRoute(GatsbyProject project, String file) {
        return file.startsWith(project.path("src/pages/")) && name(file).startsWith("{");
    }

    /**
     * @return the file an import names, e.g. {@code ./header} from {@code src/components/layout.js} is
     *         {@code src/components/header.js}; null if it is not one of the files
     */
    private static String resolve(String from, String module, List<String> files) {
        int slash = from.lastIndexOf('/');
        String base = GatsbyProject.normalise((slash < 0 ? "." : from.substring(0, slash)) + "/" + module);
        if (base == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>(List.of(base));
        for (String extension : EXTENSIONS) {
            candidates.add(base + extension);
            candidates.add(base + "/index" + extension);
        }
        return candidates.stream().filter(files::contains).findFirst().orElse(null);
    }

    private static String name(String file) {
        return file.substring(file.lastIndexOf('/') + 1);
    }
}
