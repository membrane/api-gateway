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

package com.predic8.membrane.core.interceptor.sqlinjection;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.predic8.membrane.core.interceptor.sqlinjection.Transformation.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TransformationTest {

    @Nested
    class UrlDecodeUni {

        @Test
        void decodesPercentEscapes() {
            assertEquals("' UNION", urlDecodeUni.apply("%27%20UNION"));
            assertEquals("/*", urlDecodeUni.apply("%2f%2A")); // lower- and upper-case hex digits
        }

        @Test
        void plusBecomesSpace() {
            assertEquals("a b c", urlDecodeUni.apply("a+b+c"));
        }

        @Test
        void decodedEscapeMayItselfBePercent() {
            assertEquals("100%", urlDecodeUni.apply("100%25"));
        }

        @Test
        void decodesMicrosoftUEscape() {
            assertEquals("A", urlDecodeUni.apply("%u0041"));
            assertEquals("A", urlDecodeUni.apply("%U0041")); // uppercase U accepted
        }

        @Test
        void leavesInputWithoutEscapesUnchanged() {
            assertEquals("plain", urlDecodeUni.apply("plain"));
        }

        @Test
        void leavesTruncatedPercentEscapeVerbatim() {
            assertEquals("%2", urlDecodeUni.apply("%2"));   // not enough hex digits
            assertEquals("%", urlDecodeUni.apply("%"));     // trailing percent
        }

        @Test
        void leavesNonHexPercentEscapeVerbatim() {
            assertEquals("%GG", urlDecodeUni.apply("%GG"));
        }

        @Test
        void leavesInvalidUEscapeVerbatim() {
            assertEquals("%u041", urlDecodeUni.apply("%u041"));    // truncated %uXXXX
            assertEquals("%uZZZZ", urlDecodeUni.apply("%uZZZZ"));  // non-hex digits
        }
    }

    @Nested
    class Utf8ToUnicode {

        @Test
        void foldsLatin1BytesToUtf8() {
            // Latin-1 bytes 0xC3 0xA9 are the UTF-8 encoding of 'é'.
            String encoded = new String(new byte[]{(byte) 0xC3, (byte) 0xA9}, StandardCharsets.ISO_8859_1);
            assertEquals("é", utf8toUnicode.apply(encoded));
        }

        @Test
        void leavesPlainAsciiUnchanged() {
            assertEquals("SELECT 1", utf8toUnicode.apply("SELECT 1"));
        }

        @Test
        void leavesStringWithNonByteCharsUnchanged() {
            // A char above 0xFF means the input is not a Latin-1 byte view, so it is returned untouched.
            String withWideChar = "€ SELECT"; // euro sign, U+20AC > 0xFF
            assertSame(withWideChar, utf8toUnicode.apply(withWideChar));
        }
    }

    @Nested
    class ReplaceComments {

        @Test
        void replacesTerminatedCommentWithSpace() {
            assertEquals("a b", replaceComments.apply("a/*x*/b"));
            assertEquals("1 2", replaceComments.apply("1/**/2"));       // empty comment
            assertEquals("SEL ECT", replaceComments.apply("SEL/*c*/ECT")); // inside a token
        }

        @Test
        void replacesUnterminatedCommentToEndWithSpace() {
            assertEquals("a ", replaceComments.apply("a/*x"));
        }

        @Test
        void leavesInputWithoutCommentUnchanged() {
            assertEquals("no comment", replaceComments.apply("no comment"));
        }

        @Test
        void handlesMultilineComment() {
            assertEquals("a b", replaceComments.apply("a/*line1\nline2*/b"));
        }
    }

    @Nested
    class RemoveCommentsChar {

        @Test
        void stripsCommentDelimiters() {
            assertEquals("abc", removeCommentsChar.apply("a/*b*/c"));
            assertEquals("12", removeCommentsChar.apply("1--2"));
            assertEquals("34", removeCommentsChar.apply("3#4"));
        }

        @Test
        void removesOnlyTheDelimitersNotSurroundingChars() {
            // Delimiters are deleted in place; the space that sat between them survives.
            assertEquals(" ", removeCommentsChar.apply("/*-- #"));
        }

        @Test
        void leavesInputWithoutDelimitersUnchanged() {
            assertEquals("clean", removeCommentsChar.apply("clean"));
        }
    }

    @Nested
    class RemoveWhitespace {

        @Test
        void removesAllWhitespaceKinds() {
            assertEquals("abcdef", removeWhitespace.apply("a b\tc\nd\re f"));
        }

        @Test
        void removesVerticalTabAndNbsp() {
            assertEquals("ab", removeWhitespace.apply("ab"));   // vertical tab
            assertEquals("ab", removeWhitespace.apply("a b"));   // non-breaking space
        }

        @Test
        void leavesNonWhitespaceUnchanged() {
            assertEquals("SELECT*FROMt", removeWhitespace.apply("SELECT * FROM t"));
        }
    }

    @Nested
    class ApplyAll {

        @Test
        void appliesTransformsInOrder() {
            // urlDecode reveals a comment, replaceComments swaps it for a space, removeWhitespace collapses spacing.
            String result = Transformation.applyAll("%27%20OR%201/*x*/=1",
                    List.of(urlDecodeUni, replaceComments, removeWhitespace));
            assertEquals("'OR1=1", result);
        }

        @Test
        void emptyTransformListReturnsInputUnchanged() {
            assertEquals("untouched", Transformation.applyAll("untouched", List.of()));
        }

        @Test
        void exposesKeywordHiddenByInlineComment() {
            assertEquals("UNION", Transformation.applyAll("UN/**/ION", List.of(removeCommentsChar)));
        }
    }
}
