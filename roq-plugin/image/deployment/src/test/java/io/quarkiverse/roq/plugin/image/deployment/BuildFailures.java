package io.quarkiverse.roq.plugin.image.deployment;

/** The messages of a build failure and its causes, for assertions on Roq errors. */
final class BuildFailures {

    private BuildFailures() {
    }

    static String messages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
