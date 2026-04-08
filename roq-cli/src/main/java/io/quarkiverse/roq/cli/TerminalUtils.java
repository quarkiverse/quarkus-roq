package io.quarkiverse.roq.cli;

import java.io.IOException;
import java.util.Locale;

/**
 * Lightweight terminal utilities using raw ANSI escape codes.
 * No external dependencies beyond the JDK.
 */
public final class TerminalUtils {

    // ANSI SGR codes
    private static final String ESC = "\033[";
    private static final String RESET = ESC + "0m";
    private static final String BOLD = ESC + "1m";
    private static final String FAINT = ESC + "2m";
    private static final String UNDERLINE = ESC + "4m";
    private static final String YELLOW_FG = ESC + "33m";

    // OSC 8 hyperlink sequences
    private static final String OSC8_OPEN = "\033]8;;";
    private static final String OSC8_CLOSE = "\033]8;;\033\\";
    private static final String ST = "\033\\";

    private static final Boolean OSC8_SUPPORTED = detectOsc8();
    private static String savedTermState;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(TerminalUtils::restoreTerminal));
    }

    private TerminalUtils() {
    }

    // --- Styling helpers ---

    public static String bold(String text) {
        return BOLD + text + RESET;
    }

    public static String faint(String text) {
        return FAINT + text + RESET;
    }

    public static String underline(String text) {
        return UNDERLINE + text + RESET;
    }

    public static String yellow(String text) {
        return YELLOW_FG + text + RESET;
    }

    /**
     * Render a clickable hyperlink. When the terminal supports OSC 8, the link text
     * is clickable and the URL is hidden. Otherwise the URL is shown as underlined
     * text after the label.
     */
    public static String link(String label, String url) {
        if (OSC8_SUPPORTED) {
            return OSC8_OPEN + url + ST + UNDERLINE + label + RESET + OSC8_CLOSE;
        }
        return label + "  " + FAINT + UNDERLINE + url + RESET;
    }

    /**
     * Render a clickable hyperlink where the visible text IS the URL.
     */
    public static String link(String url) {
        if (OSC8_SUPPORTED) {
            return OSC8_OPEN + url + ST + UNDERLINE + url + RESET + OSC8_CLOSE;
        }
        return FAINT + UNDERLINE + url + RESET;
    }

    public static boolean supportsOsc8() {
        return OSC8_SUPPORTED;
    }

    // --- Raw mode helpers (Unix stty, line-based fallback on Windows) ---

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Enter raw mode so single keypresses can be read from System.in.
     * Returns true if raw mode was activated, false if fallback is needed.
     */
    public static boolean enterRawMode() {
        if (isWindows()) {
            return false;
        }
        try {
            savedTermState = sttyRead("-g");
            stty("-icanon", "-echo");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Restore the terminal to its original state.
     */
    public static void restoreTerminal() {
        if (isWindows() || savedTermState == null) {
            return;
        }
        try {
            stty(savedTermState);
        } catch (Exception ignored) {
        }
        savedTermState = null;
    }

    /**
     * Read a single keypress from System.in (requires raw mode).
     * Returns the character code, or -1 on EOF.
     */
    public static int readKey() throws IOException {
        return System.in.read();
    }

    /**
     * Clear the current terminal line (move to column 0 and erase).
     */
    public static void clearLine() {
        System.out.print("\r" + ESC + "K");
        System.out.flush();
    }

    // --- Internal ---

    private static Boolean detectOsc8() {
        String termProgram = System.getenv("TERM_PROGRAM");
        if (termProgram != null) {
            String tp = termProgram.toLowerCase(Locale.ROOT);
            if (tp.contains("iterm") || tp.contains("wezterm") || tp.contains("kitty")
                    || tp.contains("vscode") || tp.contains("warp")
                    || tp.contains("ghostty") || tp.contains("alacritty")) {
                return true;
            }
        }
        if (System.getenv("WT_SESSION") != null) {
            return true;
        }
        if (System.getenv("VTE_VERSION") != null) {
            return true;
        }
        return false;
    }

    private static void stty(String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "stty";
        System.arraycopy(args, 0, cmd, 1, args.length);
        new ProcessBuilder(cmd)
                .inheritIO()
                .start()
                .waitFor();
    }

    private static String sttyRead(String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "stty";
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process p = new ProcessBuilder(cmd)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .start();
        String result = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();
        return result;
    }
}
