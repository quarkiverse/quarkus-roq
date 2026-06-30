package io.quarkiverse.roq.plugin.hybrid.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

public class FileSystemCacheStore implements RoqCacheStore {

    private static final Logger LOG = Logger.getLogger(FileSystemCacheStore.class);

    private final Path cacheDir;

    public FileSystemCacheStore(Path cacheDir) {
        this.cacheDir = cacheDir;
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create cache directory: " + cacheDir, e);
        }
        LOG.infof("Filesystem cache store initialized at: %s", cacheDir);
    }

    @Override
    public CacheEntry get(String key) {
        Path file = resolve(key);
        if (Files.exists(file)) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                long lastModified = Files.getLastModifiedTime(file).toMillis();
                return new CacheEntry(content, lastModified);
            } catch (IOException e) {
                LOG.warnf(e, "Failed to read cached file: %s", file);
            }
        }
        return null;
    }

    @Override
    public void put(String key, String value) {
        Path file = resolve(key);
        if (Files.exists(file)) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), ".roq-cache-", ".tmp");
            Files.writeString(tmp, value, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.warnf(e, "Failed to write cache file: %s", file);
        }
    }

    @Override
    public void invalidate(String key) {
        Path file = resolve(key);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.warnf(e, "Failed to delete cached file: %s", file);
        }
    }

    @Override
    public void invalidateAll() {
        try {
            deleteContents(cacheDir);
        } catch (IOException e) {
            LOG.warnf(e, "Failed to clear cache directory: %s", cacheDir);
        }
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteContents(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (!d.equals(dir)) {
                    Files.delete(d);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public long size() {
        AtomicLong count = new AtomicLong(0);
        try {
            if (Files.exists(cacheDir)) {
                Files.walkFileTree(cacheDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        count.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            LOG.warnf(e, "Failed to count cached files");
        }
        return count.get();
    }

    private Path resolve(String key) {
        String safePath = key.replace("::", "__");
        if (safePath.isEmpty() || safePath.equals("/")) {
            safePath = "index";
        }
        if (!safePath.contains(".")) {
            safePath = safePath + ".html";
        }
        Path resolved = cacheDir.resolve(safePath).normalize();
        if (!resolved.startsWith(cacheDir)) {
            throw new IllegalArgumentException("Cache key resolves outside cache directory: " + key);
        }
        return resolved;
    }
}
