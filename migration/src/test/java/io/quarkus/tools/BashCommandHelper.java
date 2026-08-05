package io.quarkus.tools;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper to find bash executable across platforms.
 * On Windows, locates Git Bash which is available by default in GitHub Actions.
 */
final class BashCommandHelper {

    private BashCommandHelper() {
    }

    /**
     * Returns the appropriate bash command for the current platform.
     * On Windows, tries to locate Git Bash in common installation paths.
     * On Unix-like systems, returns "bash".
     *
     * @return bash executable path or command
     */
    static String getBashCommand() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // On Windows, try to find Git Bash (available by default in GitHub Actions)
            String[] possiblePaths = {
                    "C:/Program Files/Git/bin/bash.exe",
                    "C:/Program Files/Git/usr/bin/bash.exe",
                    "C:/Program Files (x86)/Git/bin/bash.exe",
                    "C:/Program Files (x86)/Git/usr/bin/bash.exe"
            };

            for (String path : possiblePaths) {
                if (Files.exists(Path.of(path))) {
                    return path;
                }
            }

            // Fallback to just "bash" and hope it's in PATH (Git Bash is usually added to PATH)
            return "bash";
        }

        // On Unix-like systems (Linux, macOS), use bash directly
        return "bash";
    }

    /**
     * Converts a Windows path to Git Bash format.
     * E.g., "D:\path\to\file" becomes "/d/path/to/file"
     *
     * @param path the path to convert
     * @return Unix-style path for Git Bash on Windows, or the original path on Unix
     */
    static String toUnixPath(String path) {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // Convert Windows path to Git Bash format: D:\path\to -> /d/path/to
            String unixPath = path.replace('\\', '/');

            // Convert drive letter: C:/ -> /c/
            if (unixPath.length() >= 2 && unixPath.charAt(1) == ':') {
                char drive = Character.toLowerCase(unixPath.charAt(0));
                unixPath = "/" + drive + unixPath.substring(2);
            }

            return unixPath;
        }

        // On Unix-like systems, return as-is
        return path;
    }
}
