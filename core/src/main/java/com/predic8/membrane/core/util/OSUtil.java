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

import static com.predic8.membrane.core.util.OSUtil.OS.*;

public class OSUtil {
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    public static boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    public static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase().contains("inx");
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
}
