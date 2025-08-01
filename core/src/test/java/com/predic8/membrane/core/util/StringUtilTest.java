package com.predic8.membrane.core.util;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.util.StringUtil.*;
import static org.junit.jupiter.api.Assertions.*;

class StringUtilTest {

    private static final String poem = "To the greene forest so pleasant and faire";

    @Nested
    class TruncateAfterTests {

        @Test
        void shouldReturnEmptyWhenMaxLengthIsZero() {
            assertEquals("", truncateAfter(poem, 0));
        }

        @Test
        void shouldTruncateCorrectlyAtGivenLength() {
            assertEquals("To the greene", truncateAfter(poem, 13));
        }

        @Test
        void shouldReturnOriginalWhenMaxLengthEqualsInputLength() {
            assertEquals(poem, truncateAfter(poem, poem.length()));
        }

        @Test
        void shouldReturnOriginalWhenMaxLengthExceedsInputLength() {
            assertEquals(poem, truncateAfter(poem, 1000));
        }

        @Test
        void shouldNotTruncateWhenOnlyPrintableCharsPresent() {
            String input = "GET /index.html HTTP/1.1";
            assertEquals(input, truncateAfter(input, 50));
        }

        @Test
        void shouldTruncateProperlyWhenMaxLengthIsSmaller() {
            String input = "This is a long line that should be cut off early.";
            assertEquals("This is a long line that should be cut off ea", truncateAfter(input, 45));
        }

        @Test
        void shouldReturnOriginalWhenShorterThanMax() {
            String input = "Short line";
            assertEquals(input, truncateAfter(input, 100));
        }

        @Test
        void shouldReturnEmptyStringWhenInputIsEmpty() {
            assertEquals("", truncateAfter("", 10));
        }

        @Test
        void shouldReturnEmptyStringWhenLengthIsZero() {
            assertEquals("", truncateAfter("test", 0));
        }
    }

    @Nested
    class MaskNonPrintableCharactersTests {

        @Test
        void shouldReturnSameStringWhenOnlyPrintableCharactersExist() {
            String input = "Hello, World! 123";
            assertEquals(input, maskNonPrintableCharacters(input));
        }

        @Test
        void shouldMaskNonPrintableCharacters() {
            assertEquals("_?U__Z___huv_D", maskNonPrintableCharacters("\u00e6?\u0055\u00d6\u00ff\u005a\u00a9\u00ae\u00a7huv\u00a8D"));
        }

        @Test
        void shouldMaskOnlyNonPrintableCharacters() {
            assertEquals("____", maskNonPrintableCharacters("\n\r\t\b"));
        }

        @Test
        void shouldMaskMixedCharactersCorrectly() {
            assertEquals("A_B_C", maskNonPrintableCharacters("A\tB\nC"));
        }

        @Test
        void shouldReturnEmptyWhenInputIsEmpty() {
            assertEquals("", maskNonPrintableCharacters(""));
        }
    }
}
