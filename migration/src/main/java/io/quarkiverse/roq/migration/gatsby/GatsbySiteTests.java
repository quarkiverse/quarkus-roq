package io.quarkiverse.roq.migration.gatsby;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.roq.migration.SiteFiles;
import io.quarkiverse.roq.migration.engine.TargetSite;

/**
 * Keeps the tests of a Gatsby site that run against the served site, such as Puppeteer, Playwright or Cypress tests
 * and link checkers, so they keep working against the Roq site until they are converted.
 * <p>
 * They are moved to {@value #DIR}/ with what they need to run: their Jest or browser test configuration, the site's
 * {@code package.json} and lock file for {@code npm ci}, and a copy of {@code gatsby-config.js} if they read it.
 * Wherever they started {@code gatsby serve}, they start {@code serve-roq.js}, which serves {@code target/roq/} on the
 * same port.
 */
final class GatsbySiteTests {

    static final String DIR = "site-tests";

    static final Pattern TEST_FILE = Pattern.compile(".*\\.(test|spec)\\.(jsx?|tsx?|mjs|cjs)$");
    /**
     * Signs that a test loads pages from a running site.
     */
    static final Pattern SERVED_SITE = Pattern.compile(
            "page\\.goto\\(|cy\\.visit\\(|linkinator|https?://localhost:\\d+|jest-puppeteer\\.config|playwright");
    static final Pattern BROWSER_TEST_CONFIG = Pattern.compile(
            "(jest-puppeteer|playwright|cypress)\\.config\\.(js|cjs|mjs|ts)");
    static final Pattern JEST_CONFIG = Pattern.compile("jest(\\.[\\w-]+)?\\.config\\.(js|cjs|mjs|ts)");
    /**
     * Jest transforms test code with Babel, so its configuration goes with the tests.
     */
    static final Pattern BABEL_CONFIG = Pattern.compile("babel\\.config\\.(js|cjs|mjs|json)|\\.babelrc(\\.\\w+)?");
    /**
     * A local file a config refers to, such as {@code "./jest-preprocess.js"} or {@code "<rootDir>/setup.js"}.
     */
    static final Pattern LOCAL_FILE = Pattern.compile("[\"'`](?:\\./|<rootDir>/)([\\w.-]+\\.(?:js|cjs|mjs|json))[\"'`]");
    static final List<String> PACKAGE_FILES = List.of("package.json", "package-lock.json", "yarn.lock",
            "pnpm-lock.yaml", ".nvmrc");
    static final Pattern GATSBY_SERVE = Pattern.compile(
            "(?:\\./node_modules/\\.bin/|npx\\s+)?gatsby\\s+serve(?:\\s+(?:-p|--port)\\s+(\\d+))?");
    static final Pattern PORT = Pattern.compile("port\\s*:\\s*(\\d+)");
    static final String DEFAULT_PORT = "9000"; // gatsby serve's

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * @param tests the kept test files, relative to the migrated directory
     * @param scripts the npm scripts that run them
     */
    record Kept(Set<String> tests, List<String> scripts) {
        static final Kept NONE = new Kept(Set.of(), List.of());
    }

    private GatsbySiteTests() {
    }

    /**
     * @return what was kept in {@value #DIR}/; {@link Kept#NONE} if the site has no such tests
     */
    static Kept keep(GatsbyProject project, TargetSite site) throws IOException {
        Path dir = site.dir();
        Path root = dir.resolve(project.path(""));
        Set<String> tests = new LinkedHashSet<>();
        for (Path test : SiteFiles.files(root, p -> TEST_FILE.matcher(p.getFileName().toString()).matches())) {
            if (SERVED_SITE.matcher(Files.readString(test)).find()) {
                tests.add(root.relativize(test).toString().replace('\\', '/'));
            }
        }
        if (tests.isEmpty()) {
            return Kept.NONE;
        }

        // Dedicated test directories go whole, with their helpers; a test among the sources goes on its own
        Set<String> kept = new LinkedHashSet<>();
        for (String test : tests) {
            int slash = test.indexOf('/');
            String top = slash < 0 ? test : test.substring(0, slash);
            kept.add(top.equals("src") ? test : top);
        }
        boolean readsConfig = false;
        for (String test : tests) {
            readsConfig |= Files.readString(root.resolve(test)).contains("gatsby-config");
        }
        for (String file : SiteFiles.files(root, p -> root.relativize(p).getNameCount() == 1).stream()
                .map(p -> p.getFileName().toString()).toList()) {
            if (BROWSER_TEST_CONFIG.matcher(file).matches() || PACKAGE_FILES.contains(file)
                    || BABEL_CONFIG.matcher(file).matches()
                    || (readsConfig && file.equals("gatsby-config.js"))
                    || (JEST_CONFIG.matcher(file).matches() && usedByKeptTests(root.resolve(file), kept))) {
                kept.add(file);
            }
        }
        if (kept.stream().anyMatch(f -> f.startsWith("cypress.config")) && Files.isDirectory(root.resolve("cypress"))) {
            kept.add("cypress");
        }
        // Files the kept configs load, such as a Jest transform or setup file
        for (String config : List.copyOf(kept)) {
            Path file = root.resolve(config);
            if (Files.isRegularFile(file) && !PACKAGE_FILES.contains(config)) {
                Matcher local = LOCAL_FILE.matcher(Files.readString(file));
                while (local.find()) {
                    if (Files.isRegularFile(root.resolve(local.group(1)))) {
                        kept.add(local.group(1));
                    }
                }
            }
        }

        Path target = dir.resolve(DIR);
        for (String file : kept) {
            Path from = root.resolve(file);
            Path to = target.resolve(file);
            if (Files.isDirectory(from)) {
                SiteFiles.copyTree(from, to);
                if (project.root().isEmpty()) {
                    SiteFiles.deleteTree(from);
                }
            } else {
                Files.createDirectories(to.getParent());
                Files.copy(from, to);
                if (project.root().isEmpty() && !PACKAGE_FILES.contains(file) && !file.equals("gatsby-config.js")) {
                    Files.delete(from);
                }
            }
        }

        // Serve the Roq site where the tests expect the Gatsby one
        String port = DEFAULT_PORT;
        for (Path file : SiteFiles.files(target, p -> !p.toString().endsWith(".md"))) {
            String text = Files.readString(file);
            Matcher serve = GATSBY_SERVE.matcher(text);
            if (serve.find()) {
                Matcher configuredPort = PORT.matcher(text);
                port = serve.group(1) != null ? serve.group(1) : configuredPort.find() ? configuredPort.group(1) : port;
                String replaced = GATSBY_SERVE.matcher(text).replaceAll(m -> Matcher.quoteReplacement(
                        "node serve-roq.js " + (m.group(1) != null ? m.group(1) : portOf(text))));
                Files.writeString(file, replaced);
            }
        }
        List<String> scripts = scripts(target.resolve("package.json"), kept);
        List<String> commands = !scripts.isEmpty() ? scripts.stream().map(s -> "npm run " + s).toList()
                : kept.stream().anyMatch(f -> f.startsWith("playwright.config")) ? List.of("npx playwright test")
                        : kept.stream().anyMatch(f -> f.startsWith("cypress.config")) ? List.of("npx cypress run")
                                : List.of("npx jest");
        Files.writeString(target.resolve("serve-roq.js"), GatsbyTemplates.resource("site-tests/serve-roq.js"));
        Files.writeString(target.resolve("README.md"), GatsbyTemplates.resource("site-tests/README.md")
                .replace("${port}", port)
                .replace("${commands}", String.join("\n", commands)));

        Set<String> keptTests = new LinkedHashSet<>();
        tests.forEach(t -> keptTests.add(project.path(t)));
        site.report().info("Kept the " + tests.size() + " tests that run against the served site in " + DIR
                + "/; they still work, against the Roq site served by " + DIR + "/serve-roq.js on port " + port
                + " (see " + DIR + "/README.md)");
        site.report().todo("Convert the tests in " + DIR + "/ to Java tests (@RoqAndRoll with Playwright), then "
                + "delete " + DIR + "/; until then they run with npm, see " + DIR + "/README.md");
        return new Kept(keptTests, scripts);
    }

    /**
     * @return the names of the npm scripts that run the kept tests
     */
    static List<String> scripts(Path packageJson, Set<String> kept) throws IOException {
        List<String> names = new ArrayList<>();
        if (Files.exists(packageJson)) {
            JsonNode scripts = JSON.readTree(packageJson.toFile()).path("scripts");
            scripts.fieldNames().forEachRemaining(name -> {
                // Scripts that only exclude the kept tests, such as the unit test script, don't run them
                String script = scripts.get(name).asText().replaceAll("--testPathIgnorePatterns[ =]\\S+", "");
                if (kept.stream().filter(k -> !PACKAGE_FILES.contains(k) && !k.equals("gatsby-config.js"))
                        .anyMatch(script::contains)) {
                    names.add(name);
                }
            });
        }
        return names;
    }

    private static boolean usedByKeptTests(Path jestConfig, Set<String> kept) throws IOException {
        String text = Files.readString(jestConfig);
        return SERVED_SITE.matcher(text).find() || text.contains("puppeteer") || kept.stream().anyMatch(text::contains);
    }

    private static String portOf(String text) {
        Matcher port = PORT.matcher(text);
        return port.find() ? port.group(1) : DEFAULT_PORT;
    }
}
