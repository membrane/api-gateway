package com.predic8.membrane.common;


/**
 * Mini Duplication of a class from core.
 * TODO: Handle common used code!
 *
 * ANSI terminal color codes for error message rendering.
 */
public final class TerminalColorsMini {

    public static final String MEMBRANE_DISABLE_TERM_COLORS_PROPERTY = "membrane.disable.term.colors";

    private static final boolean ENABLED = detectAnsiSupport();

    private TerminalColorsMini() {
    }

    private static final String RESET_ = "\u001B[0m";
    private static final String RED_ = "\u001B[31m";
    private static final String YELLOW_ = "\u001B[33m";
    private static final String CYAN_ = "\u001B[36m";
    private static final String BOLD_ = "\u001B[1m";

    public static String RESET() {
        return ENABLED ? RESET_ : "";
    }

    public static String RED() {
        return ENABLED ? RED_ : "";
    }

    public static String YELLOW() {
        return ENABLED ? YELLOW_ : "";
    }

    public static String CYAN() {
        return ENABLED ? CYAN_ : "";
    }

    public static String BOLD() {
        return ENABLED ? BOLD_ : "";
    }

    private static boolean detectAnsiSupport() {
        String prop = System.getProperty(MEMBRANE_DISABLE_TERM_COLORS_PROPERTY);
        return prop != null && !Boolean.parseBoolean(prop);
    }
}
