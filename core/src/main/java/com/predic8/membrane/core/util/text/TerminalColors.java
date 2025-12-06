/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.text;

import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.StringUtil;

import static com.predic8.membrane.core.util.StringUtil.no;
import static com.predic8.membrane.core.util.StringUtil.yes;

//import static com.predic8.membrane.core.util.StringUtil.no;
//import static com.predic8.membrane.core.util.text.StringUtil.yes;

@SuppressWarnings("unused")
public final class TerminalColors {

    private static volatile boolean enabled;

    static {
       enabled = detectAnsiSupport();
    }

    private TerminalColors() {
    }

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    // Base ANSI codes
    private static final String RESET_        = "\u001B[0m";

    private static final String BLACK_        = "\u001B[30m";
    private static final String RED_          = "\u001B[31m";
    private static final String GREEN_        = "\u001B[32m";
    private static final String YELLOW_       = "\u001B[33m";
    private static final String BLUE_         = "\u001B[34m";
    private static final String MAGENTA_      = "\u001B[35m";
    private static final String CYAN_         = "\u001B[36m";
    private static final String WHITE_        = "\u001B[37m";

    // Bright variants
    private static final String BRIGHT_BLACK_   = "\u001B[90m";
    private static final String BRIGHT_RED_     = "\u001B[91m";
    private static final String BRIGHT_GREEN_   = "\u001B[92m";
    private static final String BRIGHT_YELLOW_  = "\u001B[93m";
    private static final String BRIGHT_BLUE_    = "\u001B[94m";
    private static final String BRIGHT_MAGENTA_ = "\u001B[95m";
    private static final String BRIGHT_CYAN_    = "\u001B[96m";
    private static final String BRIGHT_WHITE_   = "\u001B[97m";

    // Styles
    private static final String BOLD_        = "\u001B[1m";
    private static final String UNDERLINE_   = "\u001B[4m";

    // Exposed getters (apply enable/disable toggle)
    public static String RESET()        { return enabled ? RESET_        : ""; }

    public static String BLACK()        { return enabled ? BLACK_        : ""; }
    public static String RED()          { return enabled ? RED_          : ""; }
    public static String GREEN()        { return enabled ? GREEN_        : ""; }
    public static String YELLOW()       { return enabled ? YELLOW_       : ""; }
    public static String BLUE()         { return enabled ? BLUE_         : ""; }
    public static String MAGENTA()      { return enabled ? MAGENTA_      : ""; }
    public static String CYAN()         { return enabled ? CYAN_         : ""; }
    public static String WHITE()        { return enabled ? WHITE_        : ""; }

    public static String BRIGHT_BLACK()   { return enabled ? BRIGHT_BLACK_   : ""; }
    public static String BRIGHT_RED()     { return enabled ? BRIGHT_RED_     : ""; }
    public static String BRIGHT_GREEN()   { return enabled ? BRIGHT_GREEN_   : ""; }
    public static String BRIGHT_YELLOW()  { return enabled ? BRIGHT_YELLOW_  : ""; }
    public static String BRIGHT_BLUE()    { return enabled ? BRIGHT_BLUE_    : ""; }
    public static String BRIGHT_MAGENTA() { return enabled ? BRIGHT_MAGENTA_ : ""; }
    public static String BRIGHT_CYAN()    { return enabled ? BRIGHT_CYAN_    : ""; }
    public static String BRIGHT_WHITE()   { return enabled ? BRIGHT_WHITE_   : ""; }

    public static String BOLD()        { return enabled ? BOLD_        : ""; }
    public static String UNDERLINE()   { return enabled ? UNDERLINE_   : ""; }

    // Semantic aliases (adjust if your project wants different defaults)
    public static String ERROR()       { return RED(); }
    public static String WARN()        { return YELLOW(); }
    public static String INFO()        { return BLUE(); }
    public static String SUCCESS()     { return GREEN(); }

    // Convenience wrappers
    public static String error(String s)       { return ERROR()   + s + RESET(); }
    public static String warn(String s)        { return WARN()    + s + RESET(); }
    public static String info(String s)        { return INFO()    + s + RESET(); }
    public static String success(String s)     { return SUCCESS() + s + RESET(); }

    public static String bold(String s)        { return BOLD()        + s + RESET(); }
    public static String underline(String s)   { return UNDERLINE()   + s + RESET(); }

    // Bright wrappers (optional)
    public static String brightRed(String s)     { return BRIGHT_RED()     + s + RESET(); }
    public static String brightGreen(String s)   { return BRIGHT_GREEN()   + s + RESET(); }
    public static String brightYellow(String s)  { return BRIGHT_YELLOW()  + s + RESET(); }
    public static String brightBlue(String s)    { return BRIGHT_BLUE()    + s + RESET(); }
    public static String brightMagenta(String s) { return BRIGHT_MAGENTA() + s + RESET(); }
    public static String brightCyan(String s)    { return BRIGHT_CYAN()    + s + RESET(); }
    public static String brightWhite(String s)   { return BRIGHT_WHITE()   + s + RESET(); }

    public static boolean detectAnsiSupport() {

        // User override always wins
        String override = System.getenv("MEMBRANE_COLORS");
        if (yes(override)) return true;
        if (no(override))  return false;

        // CI environments usually strip ANSI
        if (System.getenv("CI") != null)
            return false;

        // IntelliJ terminal + run window
        if (System.getenv("JETBRAINS_IDE") != null
            || System.getenv("IDEA_INITIAL_DIRECTORY") != null
            || System.getProperty("java.class.path", "").contains("idea_rt"))
            return true;

        // VSCode sets TERM_PROGRAM
        if ("vscode".equalsIgnoreCase(System.getenv("TERM_PROGRAM")))
            return true;

        // Modern macOS terminals & iTerm2
        if (System.getenv("TERM_PROGRAM") != null)
            return true;

        // TERM value
        String term = System.getenv("TERM");
        if (term != null && !term.equals("dumb"))
            return true;

        // Windows
        if (OSUtil.isWindows()) {
            if (System.getenv("WT_SESSION") != null) return true;
            if (System.getenv("TERM_PROGRAM") != null) return true;
            return false; // classic cmd/powershell
        }

        // LAST resort:
        // System.console() is NOT reliable (IDE == null, Docker == null)
        return false;
    }
}
