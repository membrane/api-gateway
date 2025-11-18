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

package com.predic8.membrane.annot.generator.util;

public class SchemaGeneratorUtil {

    public static String escapeJsonContent(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(escape(c));
        }
        return sb.toString();
    }

    static String escape(char c) {
        return switch (c) {
            case '"' -> "\\\"";
            case '\\' -> "\\\\";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> {
                if (c < 0x20) yield String.format("\\u%04x", (int) c);
                else yield String.valueOf(c);
            }
        };
    }
}
