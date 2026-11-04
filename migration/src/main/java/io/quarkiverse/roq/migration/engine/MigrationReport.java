package io.quarkiverse.roq.migration.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.qute.Engine;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.Template;

/**
 * Collects messages from the migration stages. Messages go to the {@link MigrationOutput} as they arrive;
 * warnings and action items are also kept so they can be summarised once the migration finishes.
 */
public class MigrationReport {

    /**
     * The checklist of action items written to the converted site.
     */
    public static final String ACTION_ITEMS_FILE = "ROQ-MIGRATION.md";

    private static final Engine ENGINE = Engine.builder()
            .addDefaults()
            .addValueResolver(new ReflectionValueResolver())
            .build();

    public record Warning(String stage, String message) {
    }

    /**
     * Something the user has to do by hand to finish the migration.
     */
    public record ActionItem(String stage, String message) {
    }

    private final MigrationOutput output;
    private final List<Warning> warnings = new ArrayList<>();
    private final List<ActionItem> actionItems = new ArrayList<>();
    private String currentStage;
    private Path actionItemsFile;

    MigrationReport(MigrationOutput output) {
        this.output = output;
    }

    void currentStage(String stage) {
        this.currentStage = stage;
    }

    public void info(String message) {
        output.info("  " + message);
    }

    /**
     * Report something that may be wrong with the converted site.
     */
    public void warn(String message) {
        warnings.add(new Warning(currentStage, message));
        output.warn("  " + message);
    }

    /**
     * Report something the migration could not do, which the user has to do by hand, e.g. converting a template
     * the migrator cannot translate. Action items are listed at the end of the migration and written to
     * {@value #ACTION_ITEMS_FILE} in the converted site.
     */
    public void todo(String message) {
        actionItems.add(new ActionItem(currentStage, message));
        output.info("  To do: " + message);
    }

    public List<Warning> warnings() {
        return List.copyOf(warnings);
    }

    public List<ActionItem> actionItems() {
        return List.copyOf(actionItems);
    }

    /**
     * Write the action items as a Markdown checklist, grouped by stage, to {@value #ACTION_ITEMS_FILE} in the site.
     * Does nothing if there are none, and warns instead of overwriting an existing file.
     */
    void writeActionItems(Path siteDir, SiteType siteType) throws MigrationException {
        if (actionItems.isEmpty()) {
            return;
        }
        Path file = siteDir.resolve(ACTION_ITEMS_FILE);
        if (Files.exists(file)) {
            warn(ACTION_ITEMS_FILE + " already exists, so the action items were not written to it");
            return;
        }
        String markdown = actionItemsTemplate().data("report", this).data("siteType", siteType).render();
        try {
            Files.writeString(file, markdown);
        } catch (IOException e) {
            throw new MigrationException("Cannot write " + file + ": " + e.getMessage(), e);
        }
        actionItemsFile = file;
    }

    /**
     * Print the action items, and where they were written; meant to be the last thing the migration prints.
     */
    void printActionItems() {
        if (actionItems.isEmpty()) {
            return;
        }
        output.info("");
        output.warn(actionItems.size() == 1 ? "1 thing needs your attention:"
                : actionItems.size() + " things need your attention:");
        actionItemsByStage().forEach((stage, messages) -> messages
                .forEach(message -> output.warn("  [" + stage + "] " + message)));
        if (actionItemsFile != null) {
            output.info("They are listed in " + actionItemsFile + ".");
        }
    }

    /**
     * @return the messages of the action items, by stage, in the order they were reported
     */
    public Map<String, List<String>> actionItemsByStage() {
        Map<String, List<String>> byStage = new LinkedHashMap<>();
        for (ActionItem item : actionItems) {
            byStage.computeIfAbsent(item.stage(), s -> new ArrayList<>()).add(item.message());
        }
        return byStage;
    }

    private static Template actionItemsTemplate() {
        try (InputStream in = MigrationReport.class.getResourceAsStream("/templates/" + ACTION_ITEMS_FILE)) {
            return ENGINE.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
