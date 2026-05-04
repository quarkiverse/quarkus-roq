package io.quarkiverse.roq.editor.deployment.git;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;

import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;

/**
 * Helper class for filtering significant content files in the Git repository.
 */
public class GitContentFilter {

    private final RoqSiteConfig siteConfig;
    private final File rootDirectory;

    public GitContentFilter(RoqSiteConfig siteConfig, File rootDirectory) {
        this.siteConfig = siteConfig;
        this.rootDirectory = rootDirectory;
    }

    /**
     * Extracts significant content changes from the repository status.
     *
     * @param status the JGit status object
     * @param prefix the repository prefix
     * @return a list of changed file paths that are considered significant
     */
    public List<String> extractSignificantContentChanges(Status status, String prefix) {
        return Stream
                .of(status.getUncommittedChanges(), status.getUntracked(), status.getAdded(), status.getChanged(),
                        status.getRemoved())
                .flatMap(Set::stream).filter(path -> isSignificantContentFile(path, prefix)).distinct().toList();
    }

    /**
     * Checks if a file path is considered a significant content file for Roq.
     *
     * @param path the file path
     * @param prefix the working directory prefix
     * @return true if the file is significant content
     */
    public boolean isSignificantContentFile(String path, String prefix) {
        if (path.startsWith(".git") || path.contains("/.git/"))
            return false;
        if (!prefix.isEmpty() && !path.startsWith(prefix))
            return false;
        String relativePath = prefix.isEmpty() ? path : (path.startsWith(prefix) ? path.substring(prefix.length()) : path);
        if (relativePath.startsWith("/"))
            relativePath = relativePath.substring(1);

        return relativePath.startsWith(siteConfig.contentDir()) ||
                relativePath.startsWith(siteConfig.publicDir()) ||
                relativePath.startsWith("posts/") ||
                relativePath.startsWith("data/") ||
                relativePath.startsWith("templates/") ||
                relativePath.equals("roq.java");
    }

    /**
     * Resolves the prefix of the working directory relative to the repository root.
     *
     * @param repository the JGit repository
     * @return the relative path prefix
     */
    public String resolveWorkingPrefix(Repository repository) {
        Path rootPath = repository.getWorkTree().toPath().toAbsolutePath().normalize();
        Path currentPath = rootDirectory.toPath().toAbsolutePath().normalize();
        if (currentPath.equals(rootPath))
            return "";
        return rootPath.relativize(currentPath).toString().replace(File.separatorChar, '/') + "/";
    }
}
