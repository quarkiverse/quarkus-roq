package io.quarkiverse.roq.generator.runtime.devui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.generator.runtime.RoqGenerator;
import io.quarkiverse.roq.generator.runtime.RoqGeneratorConfig;
import io.quarkiverse.roq.generator.runtime.RoqSelection;
import io.quarkiverse.roq.generator.runtime.SelectedPath;
import io.quarkus.arc.All;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RoqDevUiJsonRPCService {

    private static final Logger LOGGER = Logger.getLogger(RoqDevUiJsonRPCService.class);

    @All
    @Inject
    List<RoqSelection> selection;

    @Inject
    RoqGenerator generator;

    @Inject
    RoqGeneratorConfig config;

    @Blocking
    public List<SelectedPath> getSelection() {
        return RoqSelection.prepare(config, selection);
    }

    @Blocking
    public List<SelectedPath> getPosts() {
        return getSelection().stream().filter(p -> p.path().startsWith("/posts")).toList();
    }

    @Blocking
    public int getCount() {
        return getSelection().size();
    }

    public Uni<String> generate() {
        return generator.generate().map(a -> generator.outputDir());
    }

    @Blocking
    public String getFileContent(String path) {
        SelectedPath selectedPath;
        try {
            selectedPath = getSelectedPath(path);
        } catch (Exception e) {
            return e.getMessage();
        }

        try {
            return Files.readString(Path.of(selectedPath.sourceFilePath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.errorf(e, "Error reading source file for path: %s", path);
            return "Error reading source file: " + e.getMessage();
        }
    }

    private SelectedPath getSelectedPath(String path) throws Exception {
        if (path == null || path.isEmpty()) {
            throw new Exception("Path parameter is required");
        }

        Optional<SelectedPath> selectedPathOpt = getSelection().stream()
                .filter(e -> Objects.equals(e.path(), path))
                .findFirst();

        if (selectedPathOpt.isEmpty()) {
            throw new Exception("Path not found in selection: " + path);
        }

        SelectedPath selectedPath = selectedPathOpt.get();

        if (selectedPath.sourceFilePath() == null || selectedPath.sourceFilePath().isEmpty()) {
            throw new Exception("Source file path not available for path: " + path);
        }

        return selectedPath;
    }

    @Blocking
    public String saveFileContent(String path, String content) {
        if (content == null) {
            return "Content parameter is required";
        }

        SelectedPath selectedPath;
        try {
            selectedPath = getSelectedPath(path);
        } catch (Exception e) {
            return e.getMessage();
        }
        try {
            Path filePath = Path.of(selectedPath.sourceFilePath());
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            LOGGER.infof("Successfully saved file: %s", filePath);
            return "success";
        } catch (Exception e) {
            LOGGER.errorf(e, "Error saving source file for path: %s", path);
            return "Error saving source file: " + e.getMessage();
        }
    }
}
