package io.quarkiverse.roq.editor.deployment.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

public class RoqEditorContentService {

    private static final Logger LOG = Logger.getLogger(RoqEditorContentService.class);

    public enum Status {
        IDLE,
        PENDING,
        DONE,
        ERROR
    }

    public record FileOpStatus(Status status, String error) {
        public static final FileOpStatus IDLE = new FileOpStatus(Status.IDLE, null);
        public static final FileOpStatus PENDING = new FileOpStatus(Status.PENDING, null);
        public static final FileOpStatus DONE = new FileOpStatus(Status.DONE, null);

        public static FileOpStatus error(String message) {
            return new FileOpStatus(Status.ERROR, message);
        }
    }

    private final Path projectRoot;
    private final AtomicReference<FileOpStatus> status = new AtomicReference<>(FileOpStatus.IDLE);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RoqEditor-FileOp");
        t.setDaemon(true);
        return t;
    });

    public RoqEditorContentService(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    private void checkPath(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(projectRoot)) {
            throw new SecurityException("Path is outside project root: " + path);
        }
    }

    public FileOpStatus getStatus() {
        return status.get();
    }

    public void submitWrite(Path path, String content) {
        checkPath(path);
        checkReady();
        executor.submit(() -> run(() -> {
            Files.writeString(path, content, StandardCharsets.UTF_8);
            LOG.infof("Written: %s", path);
        }));
    }

    public void submitWriteAndRename(Path writePath, String content, Path from, Path to) {
        checkPath(writePath);
        checkPath(from);
        checkPath(to);
        checkReady();
        executor.submit(() -> run(() -> {
            // Write content before rename so data is safe if rename fails
            Files.writeString(from, content, StandardCharsets.UTF_8);
            doRename(from, to);
            LOG.infof("Written and renamed %s -> %s", from, to);
        }));
    }

    public void submitRename(Path from, Path to) {
        checkPath(from);
        checkPath(to);
        checkReady();
        executor.submit(() -> run(() -> {
            doRename(from, to);
            LOG.infof("Renamed: %s -> %s", from, to);
        }));
    }

    public void submitCreate(Path dir, Path file, String content) {
        checkPath(dir);
        checkPath(file);
        checkReady();
        executor.submit(() -> run(() -> {
            Files.createDirectories(dir);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            LOG.infof("Created: %s", file);
        }));
    }

    public void submitDelete(Path path) {
        checkPath(path);
        checkReady();
        executor.submit(() -> run(() -> {
            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.walk(path)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            LOG.errorf(e, "Failed to delete: %s", p);
                        }
                    });
                }
            } else {
                Files.deleteIfExists(path);
            }
            LOG.infof("Deleted: %s", path);
        }));
    }

    private void checkReady() {
        FileOpStatus previous = status.getAndUpdate(
                current -> current.status() == Status.PENDING ? current : FileOpStatus.PENDING);
        if (previous.status() == Status.PENDING) {
            throw new IllegalStateException("Another file operation is already in progress");
        }
    }

    private void run(IORunnable task) {
        try {
            task.run();
            status.set(FileOpStatus.DONE);
        } catch (Exception e) {
            LOG.errorf(e, "File operation failed");
            status.set(FileOpStatus.error(e.getMessage()));
        }
    }

    private void doRename(Path from, Path to) throws IOException {
        if (Files.exists(to)) {
            if (Files.isDirectory(to)) {
                try {
                    Files.delete(to);
                } catch (DirectoryNotEmptyException e) {
                    throw new IOException("Target dir already exists and is not empty: " + to);
                }
            } else {
                throw new IOException("Target already exists: " + to);
            }
        }
        Files.createDirectories(to.getParent());
        Files.move(from, to);
    }

    @FunctionalInterface
    private interface IORunnable {
        void run() throws Exception;
    }
}
