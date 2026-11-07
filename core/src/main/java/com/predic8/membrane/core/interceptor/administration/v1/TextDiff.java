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
package com.predic8.membrane.core.interceptor.administration.v1;

import java.util.*;

import static com.predic8.membrane.core.interceptor.administration.v1.ApiException.*;

/**
 * Line based diff in unified format.
 * <p>
 * Common leading and trailing lines are removed first; the rest is compared with a longest common subsequence, which
 * is limited to {@value #MAX_CHANGED_LINES} lines per side.
 */
final class TextDiff {

    static final int MAX_CHANGED_LINES = 2000;
    private static final int CONTEXT = 3;

    private TextDiff() {
    }

    private record Line(char op, String text, int leftNo, int rightNo) {
    }

    static String unified(String left, String right, String leftName, String rightName) {
        final var a = lines(left);
        final var b = lines(right);
        if (a.equals(b))
            return "";

        int prefix = 0;
        while (prefix < a.size() && prefix < b.size() && a.get(prefix).equals(b.get(prefix)))
            prefix++;
        int suffix = 0;
        while (suffix < a.size() - prefix && suffix < b.size() - prefix
               && a.get(a.size() - 1 - suffix).equals(b.get(b.size() - 1 - suffix)))
            suffix++;

        final var midA = a.subList(prefix, a.size() - suffix);
        final var midB = b.subList(prefix, b.size() - suffix);
        if (midA.size() > MAX_CHANGED_LINES || midB.size() > MAX_CHANGED_LINES)
            throw badRequest("The messages differ in more than %d lines and are too large to compare.".formatted(MAX_CHANGED_LINES));

        final var script = new ArrayList<Line>();
        for (int i = 0; i < prefix; i++)
            script.add(new Line(' ', a.get(i), i + 1, i + 1));
        script.addAll(lcs(midA, midB, prefix));
        for (int i = 0; i < suffix; i++) {
            final var ai = a.size() - suffix + i;
            final var bi = b.size() - suffix + i;
            script.add(new Line(' ', a.get(ai), ai + 1, bi + 1));
        }
        return "--- " + leftName + "\n+++ " + rightName + "\n" + hunks(script);
    }

    private static List<Line> lcs(List<String> a, List<String> b, int offset) {
        final int n = a.size(), m = b.size();
        final var len = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--)
            for (int j = m - 1; j >= 0; j--)
                len[i][j] = a.get(i).equals(b.get(j)) ? len[i + 1][j + 1] + 1 : Math.max(len[i + 1][j], len[i][j + 1]);
        final var result = new ArrayList<Line>();
        int i = 0, j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && a.get(i).equals(b.get(j))) {
                result.add(new Line(' ', a.get(i), offset + i + 1, offset + j + 1));
                i++;
                j++;
            } else if (i < n && (j == m || len[i + 1][j] >= len[i][j + 1])) {
                // removed lines come before added ones, as in diff -u
                result.add(new Line('-', a.get(i), offset + i + 1, offset + j));
                i++;
            } else {
                result.add(new Line('+', b.get(j), offset + i, offset + j + 1));
                j++;
            }
        }
        return result;
    }

    private static String hunks(List<Line> script) {
        final var sb = new StringBuilder();
        int i = 0;
        while (i < script.size()) {
            if (script.get(i).op() == ' ') {
                i++;
                continue;
            }
            int start = Math.max(0, i - CONTEXT);
            int end = i;
            // extend the hunk while changes are at most 2 * CONTEXT lines apart
            int lastChange = i;
            while (end < script.size() && end - lastChange <= 2 * CONTEXT) {
                if (script.get(end).op() != ' ')
                    lastChange = end;
                end++;
            }
            end = Math.min(script.size(), lastChange + CONTEXT + 1);

            int leftCount = 0, rightCount = 0;
            for (int k = start; k < end; k++) {
                final var op = script.get(k).op();
                if (op != '+') leftCount++;
                if (op != '-') rightCount++;
            }
            final var first = script.get(start);
            final var leftStart = first.op() == '+' ? first.leftNo() + (leftCount == 0 ? 0 : 1) : first.leftNo();
            final var rightStart = first.op() == '-' ? first.rightNo() + (rightCount == 0 ? 0 : 1) : first.rightNo();
            sb.append("@@ -").append(leftStart).append(',').append(leftCount)
                    .append(" +").append(rightStart).append(',').append(rightCount).append(" @@\n");
            for (int k = start; k < end; k++)
                sb.append(script.get(k).op()).append(script.get(k).text()).append('\n');
            i = end;
        }
        return sb.toString();
    }

    private static List<String> lines(String s) {
        if (s == null || s.isEmpty())
            return List.of();
        return List.of(s.split("\r?\n", -1));
    }
}
