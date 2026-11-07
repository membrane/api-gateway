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

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class TextDiffTest {

    @Test
    void identicalTextsHaveNoDiff() {
        assertEquals("", TextDiff.unified("a\nb", "a\nb", "l", "r"));
        assertEquals("", TextDiff.unified("a\r\nb", "a\nb", "l", "r"));
    }

    @Test
    void changedLine() {
        assertEquals("""
                --- l
                +++ r
                @@ -1,3 +1,3 @@
                 a
                -b
                +B
                 c
                """, TextDiff.unified("a\nb\nc", "a\nB\nc", "l", "r"));
    }

    @Test
    void insertionAtStart() {
        assertEquals("""
                --- l
                +++ r
                @@ -1,2 +1,3 @@
                +x
                 a
                 b
                """, TextDiff.unified("a\nb", "x\na\nb", "l", "r"));
    }

    @Test
    void deletionAtEnd() {
        assertEquals("""
                --- l
                +++ r
                @@ -1,2 +1,1 @@
                 a
                -b
                """, TextDiff.unified("a\nb", "a", "l", "r"));
    }

    @Test
    void distantChangesGetSeparateHunks() {
        final var left = String.join("\n", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15");
        final var right = String.join("\n", "1", "two", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "fourteen", "15");
        final var diff = TextDiff.unified(left, right, "l", "r");
        assertEquals(2, diff.split("@@ -").length - 1, diff);
        assertTrue(diff.contains("@@ -1,5 +1,5 @@"), diff);
        assertTrue(diff.contains("@@ -11,5 +11,5 @@"), diff);
    }

    @Test
    void emptyLeftSide() {
        assertEquals("""
                --- l
                +++ r
                @@ -0,0 +1,1 @@
                +a
                """, TextDiff.unified("", "a", "l", "r"));
    }

    @Test
    void refusesHugeChanges() {
        final var left = new StringBuilder();
        final var right = new StringBuilder();
        for (int i = 0; i <= TextDiff.MAX_CHANGED_LINES; i++) {
            left.append("l").append(i).append('\n');
            right.append("r").append(i).append('\n');
        }
        assertThrows(ApiException.class, () -> TextDiff.unified(left.toString(), right.toString(), "l", "r"));
    }

    @Test
    void largeTextsWithSmallChangesAreCompared() {
        final var sb = new StringBuilder();
        for (int i = 0; i < 50_000; i++)
            sb.append("line ").append(i).append('\n');
        final var left = sb.toString();
        final var right = left.replace("line 25000\n", "changed\n");
        assertTrue(TextDiff.unified(left, right, "l", "r").contains("-line 25000\n+changed\n"));
    }
}
