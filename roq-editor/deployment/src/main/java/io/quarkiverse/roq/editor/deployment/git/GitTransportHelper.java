package io.quarkiverse.roq.editor.deployment.git;

import java.io.File;
import java.util.ServiceLoader;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.KeyPasswordProvider;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory;
import org.eclipse.jgit.util.FS;
import org.jboss.logging.Logger;

/**
 * Helper class for Git transport and authentication operations.
 */
public class GitTransportHelper {

    private static final Logger LOG = Logger.getLogger(GitTransportHelper.class);

    private static final Pattern SCP_LIKE_SSH_URL = Pattern.compile("^[^@]+@[^:]+:[^/].*$");

    public static final String ERR_AUTH_FAILED = "AUTH_FAILED:SSH authentication failed. Make sure your SSH key is loaded in your ssh-agent, "
            + "or set the EDITOR_SYNC_SSH_PASSPHRASE environment variable for a passphrase-protected key.";

    /**
     * Checks if the repository uses SSH for any remote operation (fetch or push).
     * Checks both pushurl and url — if either is SSH, returns true.
     *
     * @param repository the JGit repository
     * @return true if any remote URL uses SSH
     */
    public static boolean isSsh(Repository repository) {
        String pushUrl = repository.getConfig().getString("remote", "origin", "pushurl");
        String remoteUrl = repository.getConfig().getString("remote", "origin", "url");
        return isSshUrl(pushUrl) || isSshUrl(remoteUrl);
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
     * Creates a TransportConfigCallback for SSH authentication.
     * <p>
     * Authentication is resolved by JGit in this order:
     * <ol>
     * <li>the system SSH agent (macOS Keychain, {@code ssh-agent} on Linux, Pageant/Windows agent),
     * enabled via {@link ConnectorFactory#getDefault()};</li>
     * <li>the optional server-side {@code passphrase} for a passphrase-protected key when no agent is available.</li>
     * </ol>
     * The passphrase is never received from the browser; it comes from server-side configuration only.
     *
     * @param passphrase the SSH passphrase resolved from server configuration, or null/empty when none is set
     * @return the configured callback
     */
    private static ConnectorFactory loadConnectorFactory() {
        ConnectorFactory factory = ServiceLoader.load(ConnectorFactory.class, GitTransportHelper.class.getClassLoader())
                .findFirst()
                .orElse(null);
        if (factory == null) {
            LOG.debug("No SSH agent ConnectorFactory found via ServiceLoader. SSH agent authentication will not be available.");
        }
        return factory;
    }

    public static TransportConfigCallback createTransportCallback(String passphrase) {
        ConnectorFactory connectorFactory = loadConnectorFactory();
        return transport -> {
            if (transport instanceof SshTransport sshTransport) {
                SshdSessionFactoryBuilder builder = new SshdSessionFactoryBuilder()
                        .setPreferredAuthentications("publickey")
                        .setConnectorFactory(connectorFactory)
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
