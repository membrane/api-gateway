/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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
