/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.util;

import org.jetbrains.annotations.*;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.predic8.membrane.core.util.OSUtil.OS.*;

public class OSUtil {

    public static final String OS_NAME_PROPERTY = "os.name";

    private static final String WINDOWS_DRIVE = detectWindowsDrive();

    public static boolean isWindows() {
        return System.getProperty(OS_NAME_PROPERTY,"").toLowerCase().contains("windows");
    }

    public static boolean isMac() {
        return System.getProperty(OS_NAME_PROPERTY,"").toLowerCase().contains("mac");
    }

    public static boolean isLinux() {
        return System.getProperty(OS_NAME_PROPERTY,"").toLowerCase().contains("inx");
    }

    public static OS getOS() {
        if (isWindows()) return WINDOWS;
        if (isLinux()) return LINUX;
        if (isMac()) return MAC;
        return UNKNOWN;
    }

    public enum OS { WINDOWS, MAC, LINUX, UNKNOWN }

    public static String fixBackslashes(String s) {
        return s.replaceAll("\\\\", "/");
    }

    public static String wl(String windows, String linux) {
        if (OSUtil.isWindows())
            return normalizeWindowsDrive(windows);
        return linux;
    }

    private static String detectWindowsDrive() {
        if (!isWindows()) return "C:";

        try {
            Path root = Paths.get("").toAbsolutePath().getRoot(); // e.g. "D:\"
            if (root == null) return "C:";
            String r = root.toString();
            return (r.length() >= 2 && r.charAt(1) == ':') ? r.substring(0, 2) : "C:";
        } catch (Exception ignored) {
            return "C:";
        }
    }

    private static String normalizeWindowsDrive(String s) {
        if (!isWindows() || s == null || "C:".equals(WINDOWS_DRIVE)) return s;

        if (s.startsWith("file:/C:")) {
            return "file:/" + WINDOWS_DRIVE + s.substring("file:/C:".length());
        }
        if (s.startsWith("C:\\")) {
            return WINDOWS_DRIVE + s.substring(2);
        }
        if (s.startsWith("C:/")) {
            return WINDOWS_DRIVE + s.substring(2);
        }
        return s;
    }
}
