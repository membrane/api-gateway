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

import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.charset.*;

import static java.lang.Integer.MAX_VALUE;

public class TextPrettifier extends AbstractPrettifier {

    public static final TextPrettifier INSTANCE = new TextPrettifier();
    private TextPrettifier() {}
    
    @Override
    public byte[] prettify(byte[] c, Charset charset) {
        return normalizeMultiline(new String(c, getCharset(charset))).getBytes(charset);
    }

    @Override
    public byte[] prettify(InputStream is, Charset charset) throws IOException {
        return prettify(is.readAllBytes(), charset);
    }

    public static String normalizeMultiline(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        
        String[] lines = splitIntoLines(input);

        Range result = getStartAndEnd(lines);

        if (result.start() > result.end()) {
            return "";
        }

        return buildNormalLines(result.start(), result.end(), lines, getMinIndent(result.start(), result.end(), lines));
    }

    private static @NotNull Range getStartAndEnd(String[] lines) {
        // Remove leading and trailing empty lines
        int start = 0;
        int end = lines.length - 1;
        while (start <= end && lines[start].isBlank()) start++;
        while (end >= start && lines[end].isBlank()) end--;
        return new Range(start, end);
    }

    private record Range(int start, int end) {
    }

    private static @NotNull String buildNormalLines(int start, int end, String[] lines, int minIndent) {
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

    private static int getMinIndent(int start, int end, String[] lines) {
        // Find smallest indentation
        int minIndent = MAX_VALUE;
        for (int i = start; i <= end; i++) {
            String line = lines[i];
            if (!line.isBlank()) {
                int leading = line.indexOf(line.trim()); // count leading whitespace
                minIndent = Math.min(minIndent, leading);
            }
        }
        return minIndent;
    }

    private static String @NotNull [] splitIntoLines(String input) {
        return input.split("\\R", -1);
    }


}
