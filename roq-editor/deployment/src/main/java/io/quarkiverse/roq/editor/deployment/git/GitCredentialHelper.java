package io.quarkiverse.roq.editor.deployment.git;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jboss.logging.Logger;

/**
 * Bridges JGit with the system's git credential helpers (osxkeychain, credential-manager, gh auth, etc.)
 * by invoking {@code git credential fill}.
 * <p>
 * JGit does not natively support {@code credential.helper} from git config.
 * This class works around that limitation for HTTPS remotes.
 * <p>
 * Credentials are cached in memory for the lifetime of the service instance (dev mode session).
 * No credential values are written to logs or disk.
 *
 * @see <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=441198">Eclipse Bug 441198</a>
 */
public class GitCredentialHelper {

    private static final Logger LOG = Logger.getLogger(GitCredentialHelper.class);
    private static final int TIMEOUT_SECONDS = 5;

    private volatile CredentialsProvider cached;
    private volatile boolean resolved;

    /**
     * Returns a cached {@link CredentialsProvider} for the origin remote,
     * obtaining it from the system's {@code git credential fill} on first call.
     *
     * @param repository the JGit repository
     * @return a configured CredentialsProvider, or null if credentials could not be obtained
     */
    public CredentialsProvider getCredentials(Repository repository) {
        if (resolved) {
            return cached;
        }
        cached = querySystemCredentialHelper(repository);
        resolved = true;
        return cached;
    }

    /**
     * Clears the cached credentials, forcing a fresh lookup on the next call.
     * Use this when an HTTPS operation fails with an authentication error.
     */
    public void invalidate() {
        cached = null;
        resolved = false;
    }

    private static CredentialsProvider querySystemCredentialHelper(Repository repository) {
        String remoteUrl = repository.getConfig().getString("remote", "origin", "url");
        if (remoteUrl == null || GitTransportHelper.isSshUrl(remoteUrl)) {
            return null;
        }

        Process process = null;
        try {
            URI uri = URI.create(remoteUrl);
            String protocol = uri.getScheme();
            String host = uri.getHost();
            if (protocol == null || host == null) {
                return null;
            }

            String input = "protocol=" + protocol + "\nhost=" + host + "\n\n";

            ProcessBuilder pb = new ProcessBuilder("git", "credential", "fill")
                    .directory(repository.getWorkTree())
                    .redirectErrorStream(true);
            process = pb.start();

            try (OutputStream os = process.getOutputStream()) {
                os.write(input.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            String username = null;
            String password = null;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("username=")) {
                        username = line.substring("username=".length());
                    } else if (line.startsWith("password=")) {
                        password = line.substring("password=".length());
                    }
                }
            }

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.debug("git credential fill timed out");
                return null;
            }

            if (process.exitValue() != 0 || username == null || password == null) {
                return null;
            }

            LOG.debug("Obtained HTTPS credentials from system credential helper");
            return new UsernamePasswordCredentialsProvider(username, password);

        } catch (Exception e) {
            LOG.debug("Failed to obtain credentials from system helper: " + e.getMessage());
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
