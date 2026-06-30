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

package com.predic8.membrane.annot.util;

import com.predic8.membrane.annot.yaml.ParsingContext;

public final class JsonPathUtil {


    private JsonPathUtil() {}

    /**
     * Returns the parent path before the last JSONPath segment.
     *
     * @param jsonPath JSONPath created by {@link ParsingContext}
     * @return parent path without the last segment
     */
    public static String getParentPath(String jsonPath) {
        return jsonPath.substring(0, findLastSegmentStart(jsonPath));
    }

    /**
     * Returns the last part of a JSONPath created by {@link ParsingContext}.
     * <p>
     * Examples:
     * {@code $.api.methods} -> {@code methods}
     * {@code $.api.methods['rpc.echo']} -> {@code rpc.echo}
     * {@code $.api.methods[0]} -> {@code 0}
     *
     * @param jsonPath JSONPath created by {@link ParsingContext}
     * @return unescaped field name or array index of the last segment
     */
    public static String getLastSegment(String jsonPath) {
        String segment = jsonPath.substring(findLastSegmentStart(jsonPath));

        if (segment.startsWith(".")) {
            return segment.substring(1);
        }

        if (isQuotedPropertySegment(segment)) {
            return decodeQuotedPropertySegment(segment);
        }

        if (isArrayIndexSegment(segment)) {
            return segment.substring(1, segment.length() - 1);
        }

        throw new IllegalArgumentException("Unsupported JSONPath segment: " + segment);
    }

    private static boolean isQuotedPropertySegment(String segment) {
        return segment.startsWith("['") && segment.endsWith("']");
    }

    private static boolean isArrayIndexSegment(String segment) {
        return segment.startsWith("[") && segment.endsWith("]");
    }

    /**
     * Removes JSONPath bracket quoting and unescapes supported characters.
     */
    private static String decodeQuotedPropertySegment(String segment) {
        return segment.substring(2, segment.length() - 2)
                .replace("\\'", "'")

                .replace("\\\\", "\\");
    }

    /**
     * Returns the start index of the last JSONPath segment.
     */
    private static int findLastSegmentStart(String jsonPath) {
        int depth = 0;
        for (int i = jsonPath.length() - 1; i >= 0; i--) {
            char c = jsonPath.charAt(i);
            if (c == ']') {
                depth++;
            } else if (c == '[') {
                depth--;
            }
            if (depth == 0 && (c == '.' || c == '[')) {
                return i;
            }
        }
        throw new IllegalArgumentException("Cannot determine parent path of: " + jsonPath);
    }
}
