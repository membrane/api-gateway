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

package com.predic8.membrane.core.prettifier;

import static java.nio.charset.StandardCharsets.*;

public class TextPrettifier implements Prettifier {

    @Override
    public byte[] prettify(byte[] c) throws Exception {
        return normalizeMultiline(new String(c, UTF_8)).getBytes(UTF_8);
    }

    public static String normalizeMultiline(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // Split into lines
        String[] lines = input.split("\\R", -1); // keep trailing empty line info

        // Remove leading and trailing empty lines
        int start = 0;
        int end = lines.length - 1;
        while (start <= end && lines[start].isBlank()) start++;
        while (end >= start && lines[end].isBlank()) end--;

        if (start > end) {
            return "";
        }

        // Find smallest indentation
        int minIndent = Integer.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            String line = lines[i];
            if (!line.isBlank()) {
                int leading = line.indexOf(line.trim()); // count leading whitespace
                minIndent = Math.min(minIndent, leading);
            }
        }

        // Build normalized lines
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            String line = lines[i];
            String normalized;
            if (line.isBlank()) {
                normalized = "";
            } else {
                String trimmedEnd = line.stripTrailing();
                normalized = trimmedEnd.substring(Math.min(minIndent, trimmedEnd.length()));
            }

            sb.append(normalized);

            // Append newline only if not the last line
            if (i < end) {
                sb.append("\n");
            }
        }

        return sb.toString();
    }


}
