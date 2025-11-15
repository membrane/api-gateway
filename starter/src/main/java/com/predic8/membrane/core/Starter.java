/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core;

import static java.lang.Integer.parseInt;

public class Starter {

    private static final int DEFAULT_REQUIRED = 21;

    public static void main(String[] args) throws Exception {

        int required = resolveRequiredMajorVersion();
        int current;

        try {
            current = currentFeatureVersion();
        } catch (Exception e) {
            System.err.println("WARNING: Could not determine Java version. Proceeding anyway...");
            current = required;
        }

        if (current < required) {
            System.err.println("---------------------------------------");
            System.err.println();
            System.err.println("Wrong Java Version!");
            System.err.println();
            System.err.println("Membrane requires Java " + required + " or newer.");
            System.err.println("The current Java feature version is " + current +
                    " (java.version=" + System.getProperty("java.version") + ").");
            System.err.println();
            System.err.println("You can check with:");
            System.err.println();
            System.err.println("java -version");
            if (System.getProperty("os.name", "").contains("Windows")) {
                System.err.println("echo %JAVA_HOME%");
            } else {
                System.err.println("echo $JAVA_HOME");
            }
            System.err.println("---------------------------------------");
            System.exit(1);
        }
    }

    private static int resolveRequiredMajorVersion() {
        String env = System.getenv("MEMBRANE_REQUIRED_JAVA_VERSION");
        if (env == null || env.isBlank())
            return DEFAULT_REQUIRED;

        try {
            return parseInt(env.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_REQUIRED;
        }
    }

    private static int currentFeatureVersion() {
        // Prefer Java 9+ Runtime.version()
        try {
            Object v = Runtime.class.getMethod("version")
                    .invoke(Runtime.getRuntime());
            try {
                // Java 10+: feature(), Java 9: major()
                return (Integer) v.getClass().getMethod("feature").invoke(v);
            } catch (NoSuchMethodException e) {
                return (Integer) v.getClass().getMethod("major").invoke(v);
            }
        } catch (Throwable ignored) {
            // On Java 8 or any failure: fall back to java.version parsing
        }
        return parseMajorFromJavaVersion(System.getProperty("java.version", ""));
    }

    /**
     * Parses values like:
     * - "1.8.0_371" -> 8
     * - "17.0.10"   -> 17
     * - "23.0.2"    -> 23
     * - "23-ea"     -> 23
     */
    private static int parseMajorFromJavaVersion(String version) {
        if (version == null || version.isBlank())
            throw new IllegalArgumentException("Empty java.version");

        String v = version.trim();
        int start = 0;
        while (start < v.length() && !Character.isDigit(v.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < v.length()) {
            char c = v.charAt(end);
            if (Character.isDigit(c) || c == '.' || c == '_' || c == '-') {
                end++;
            } else {
                break;
            }
        }
        if (start >= end)
            throw new IllegalArgumentException("No numeric token in java.version: " + version);

        String[] parts = v.substring(start, end).split("[._-]+");
        if (parts.length == 0)
            throw new IllegalArgumentException("Cannot split java.version: " + version);

        return parseInt(("1".equals(parts[0]) && parts.length > 1) ? parts[1] : parts[0]);
    }
}