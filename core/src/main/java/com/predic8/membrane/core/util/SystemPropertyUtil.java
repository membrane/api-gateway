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

package com.predic8.membrane.core.util;

/**
 * Reads tuning settings given as JVM system properties.
 */
public final class SystemPropertyUtil {

    private SystemPropertyUtil() {
    }

    /**
     * @param name         the name of the system property
     * @param defaultValue the value if the property is not set
     * @return the value of the property, or {@code defaultValue} if it is not set
     * @throws IllegalArgumentException naming the property, if its value is not a positive integer. A size of zero
     *                                  would make a read loop spin without progress.
     */
    public static int getPositiveIntProperty(String name, int defaultValue) {
        final var value = System.getProperty(name);
        if (value == null)
            return defaultValue;
        try {
            final var parsed = Integer.parseInt(value);
            if (parsed > 0)
                return parsed;
        } catch (NumberFormatException e) {
            throw invalid(name, value, e);
        }
        throw invalid(name, value, null);
    }

    private static IllegalArgumentException invalid(String name, String value, NumberFormatException cause) {
        return new IllegalArgumentException("System property %s must be a positive integer, but is '%s'.".formatted(name, value), cause);
    }
}
