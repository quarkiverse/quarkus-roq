package io.quarkiverse.roq.editor.deployment.git;

import java.io.File;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.KeyPasswordProvider;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.eclipse.jgit.util.FS;

/**
 * Helper class for Git transport and authentication operations.
 */
public class GitTransportHelper {

    private static final Pattern SCP_LIKE_SSH_URL = Pattern.compile("^[^@]+@[^:]+:[^/].*$");

    public static final String ERR_AUTH_FAILED = "AUTH_FAILED:SSH authentication failed. Please check your passphrase.";
    public static final String ERR_AUTH_REQUIRED = "AUTH_REQUIRED:SSH passphrase required for remote operations.";

    /**
     * Checks if the repository remote URL uses SSH.
     *
     * @param repository the JGit repository
     * @return true if the remote is SSH
     */
    public static boolean isSsh(Repository repository) {
        String remoteUrl = repository.getConfig().getString("remote", "origin", "url");
        return isSshUrl(remoteUrl);
    }

    /**
     * Validates if a URL is a valid SSH Git URL.
     *
     * @param url the URL string
     * @return true if it matches SSH patterns
     */
    public static boolean isSshUrl(String url) {
        if (url == null)
            return false;
        return url.startsWith("ssh://") || url.startsWith("git@") || SCP_LIKE_SSH_URL.matcher(url).matches();
    }

    /**
     * Checks if SSH authentication is required but a passphrase is missing.
     *
     * @param repository the JGit repository
     * @param passphrase the provided passphrase
     * @return true if SSH auth is required but missing
     */
    public static boolean isAuthRequired(Repository repository, String passphrase) {
        return isSsh(repository) && (passphrase == null || passphrase.isEmpty());
    }

    /**
     * Recursively checks if an exception or its causes indicate an authentication failure.
     *
     * @param throwable the caught exception
     * @return true if it is an authentication-related error
     */
    public static boolean isAuthenticationError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.io.StreamCorruptedException
                    || current instanceof javax.security.auth.login.FailedLoginException)
                return true;
            String msg = current.getMessage();
            if (msg != null) {
                String lowerMsg = msg.toLowerCase();
                if (lowerMsg.contains("auth") || lowerMsg.contains("passphrase") || lowerMsg.contains("no keys") ||
                        lowerMsg.contains("mismatched") || lowerMsg.contains("corrupted") || lowerMsg.contains("cannot log in")
                        || lowerMsg.contains("publickey") || lowerMsg.contains("identity"))
                    return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Creates a TransportConfigCallback to handle SSH authentication using a passphrase.
     *
     * @param passphrase the SSH passphrase
     * @return the configured callback
     */
    public static TransportConfigCallback createTransportCallback(String passphrase) {
        return transport -> {
            if (transport instanceof SshTransport sshTransport) {
                SshdSessionFactoryBuilder builder = new SshdSessionFactoryBuilder()
                        .setPreferredAuthentications("publickey")
                        .setHomeDirectory(FS.DETECTED.userHome())
                        .setSshDirectory(new File(FS.DETECTED.userHome(), ".ssh"));
                if (passphrase != null && !passphrase.isEmpty()) {
                    builder.setKeyPasswordProvider(cp -> new KeyPasswordProvider() {
                        @Override
                        public char[] getPassphrase(URIish uri, int attempt) {
                            return passphrase.toCharArray();
                        }

                        @Override
                        public boolean keyLoaded(URIish uri, int attempt, Exception err) {
                            return false;
                        }

                        @Override
                        public void setAttempts(int attempts) {
                        }
                    });
                }
                sshTransport.setSshSessionFactory(builder.build(null));
            }
        };
    }
}
