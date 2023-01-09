/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.graphql;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.predic8.membrane.core.graphql.Tokenizer.Type.*;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TokenizerTest {

    @Test
    public void testPrimitiveInts() {
        assertTokenizer("0", of(INT_VALUE), of("0"));
        assertTokenizer("1", of(INT_VALUE), of("1"));
        assertTokenizer("19", of(INT_VALUE), of("19"));
        assertTokenizer("2147483647", of(INT_VALUE), of("" + 2147483647));
        assertTokenizer("-2147483648", of(INT_VALUE), of("" + -2147483648));
    }

    @Test
    public void testPrimitiveFloats() {
        assertTokenizer("0.12", of(FLOAT_VALUE), of("" + 0.12));
        assertTokenizer("1.12", of(FLOAT_VALUE), of("" + 1.12));
        assertTokenizer("1e2", of(FLOAT_VALUE), of("" + 1e2));
        assertTokenizer("1e+2", of(FLOAT_VALUE), of("" + 1e+2));
        assertTokenizer("1e-2", of(FLOAT_VALUE), of("" + 1e-2));
        assertTokenizer("1.1e2", of(FLOAT_VALUE), of("" + 1.1e2));
        assertTokenizer("1.1e+2", of(FLOAT_VALUE), of("" + 1.1e+2));
        assertTokenizer("1.1e-2", of(FLOAT_VALUE), of("" + 1.1e-2));
    }

    @Test
    public void testPrimitiveNames() {
        assertTokenizer("abC", of(NAME), of("abC"));
        assertTokenizer("_abC01", of(NAME), of("_abC01"));
    }

    @Test
    public void testPrimitivePunctuators() {
        assertTokenizer("!", of(PUNCTUATOR), of("!"));
        assertTokenizer("$", of(PUNCTUATOR), of("$"));
        assertTokenizer("&", of(PUNCTUATOR), of("&"));
        assertTokenizer("(", of(PUNCTUATOR), of("("));
        assertTokenizer(")", of(PUNCTUATOR), of(")"));
        assertTokenizer("...", of(PUNCTUATOR), of("."));
        assertTokenizer(":", of(PUNCTUATOR), of(":"));
        assertTokenizer("=", of(PUNCTUATOR), of("="));
        assertTokenizer("@", of(PUNCTUATOR), of("@"));
        assertTokenizer("[", of(PUNCTUATOR), of("["));
        assertTokenizer("]", of(PUNCTUATOR), of("]"));
        assertTokenizer("{", of(PUNCTUATOR), of("{"));
        assertTokenizer("|", of(PUNCTUATOR), of("|"));
        assertTokenizer("}", of(PUNCTUATOR), of("}"));

        assertTokenizer("%", of(), of(), true);
        assertTokenizer("..", of(), of(), true);
    }

    @Test
    public void testPrimitiveBlockStrings() {
        assertTokenizer("\"\"\"\"\"\"", of(STRING_VALUE), of(""));
        assertTokenizer("\"\"\"a string\"\"\"", of(STRING_VALUE), of("a string"));
        assertTokenizer("\"\"\"a string containing \"quotes\" \"\"\"", of(STRING_VALUE), of("a string containing \"quotes\" "));
        assertTokenizer("\"\"\"a string containing \"\"doublequotes\"\" \"\"\"", of(STRING_VALUE), of("a string containing \"\"doublequotes\"\" "));
        assertTokenizer("\"\"\"a string containing \\\"\"\"triplequotes\\\"\"\" \"\"\"", of(STRING_VALUE), of("a string containing \"\"\"triplequotes\"\"\" "));
        assertTokenizer("\"\"\"unescaped high char \u2763\"\"\"", of(STRING_VALUE), of("unescaped high char \u2763"));
        assertTokenizer("\"\"\"escaped high char \\u2763\"\"\"", of(STRING_VALUE), of("escaped high char \\u2763"));
        assertTokenizer("\"\"\"invalid char \u0007\"\"\"", of(), of(), true);
    }

    @Test
    public void testPrimitiveStrings() {
        assertTokenizer("\"\"", of(STRING_VALUE), of(""));
        assertTokenizer("\"a string\"", of(STRING_VALUE), of("a string"));
        assertTokenizer("\"special chars \\\" \\\\ \\/ \\b \\f \\n \\r \\t\"", of(STRING_VALUE), of("special chars \" \\ / \b \f \n \r \t"));
        assertTokenizer("\"unescaped high char \u2763\"", of(STRING_VALUE), of("unescaped high char \u2763"));
        assertTokenizer("\"escaped high char \\u2763\"", of(STRING_VALUE), of("escaped high char \u2763"));
        assertTokenizer("\"a string\"", of(STRING_VALUE), of("a string"));
        assertTokenizer("\"an unterminated string", of(), of(), true);
        assertTokenizer("\"an incomplete hex sequence \\u12", of(), of(), true);
        assertTokenizer("\"an incomplete hex sequence \\u12\"", of(), of(), true);
        assertTokenizer("\"invalid escaped char \\z\"", of(), of(), true);
        assertTokenizer("\"invalid char \u0007\"", of(), of(), true);
    }

    @Test
    public void testSequences() {
        assertTokenizer("0 0", of(INT_VALUE, INT_VALUE), of("0", "0"));
        assertTokenizer("0 0.1", of(INT_VALUE, FLOAT_VALUE), of("0", "0.1"));
        assertTokenizer("0 a", of(INT_VALUE, NAME), of("0", "a"));
        assertTokenizer("0!", of(INT_VALUE, PUNCTUATOR), of("0", "!"));
        assertTokenizer("0\"a\"", of(INT_VALUE, STRING_VALUE), of("0", "a"));

        assertTokenizer("0.1 0", of(FLOAT_VALUE, INT_VALUE), of("0.1", "0"));
        assertTokenizer("0.1 0.1", of(FLOAT_VALUE, FLOAT_VALUE), of("0.1", "0.1"));
        assertTokenizer("0.1 a", of(FLOAT_VALUE, NAME), of("0.1", "a"));
        assertTokenizer("0.1!", of(FLOAT_VALUE, PUNCTUATOR), of("0.1", "!"));
        assertTokenizer("0.1\"a\"", of(FLOAT_VALUE, STRING_VALUE), of("0.1", "a"));

        assertTokenizer("1e2 0", of(FLOAT_VALUE, INT_VALUE), of("1e2", "0"));
        assertTokenizer("1e2 0.1", of(FLOAT_VALUE, FLOAT_VALUE), of("1e2", "0.1"));
        assertTokenizer("1e2 a", of(FLOAT_VALUE, NAME), of("1e2", "a"));
        assertTokenizer("1e2!", of(FLOAT_VALUE, PUNCTUATOR), of("1e2", "!"));
        assertTokenizer("1e2\"a\"", of(FLOAT_VALUE, STRING_VALUE), of("1e2", "a"));

        assertTokenizer("cd 0", of(NAME, INT_VALUE), of("cd", "0"));
        assertTokenizer("cd 0.1", of(NAME, FLOAT_VALUE), of("cd", "0.1"));
        assertTokenizer("cd a", of(NAME, NAME), of("cd", "a"));
        assertTokenizer("cd!", of(NAME, PUNCTUATOR), of("cd", "!"));
        assertTokenizer("cd\"a\"", of(NAME, STRING_VALUE), of("cd", "a"));

        assertTokenizer("(0", of(PUNCTUATOR, INT_VALUE), of("(", "0"));
        assertTokenizer("(0.1", of(PUNCTUATOR, FLOAT_VALUE), of("(", "0.1"));
        assertTokenizer("(a", of(PUNCTUATOR, NAME), of("(", "a"));
        assertTokenizer("(!", of(PUNCTUATOR, PUNCTUATOR), of("(", "!"));
        assertTokenizer("(\"a\"", of(PUNCTUATOR, STRING_VALUE), of("(", "a"));

        assertTokenizer("e 0", of(NAME, INT_VALUE), of("e", "0"));
        assertTokenizer("e 0.1", of(NAME, FLOAT_VALUE), of("e", "0.1"));
        assertTokenizer("e a", of(NAME, NAME), of("e", "a"));
        assertTokenizer("e!", of(NAME, PUNCTUATOR), of("e", "!"));
        assertTokenizer("e\"a\"", of(NAME, STRING_VALUE), of("e", "a"));
    }

    @Test
    public void testIgnored() {
        assertTokenizer("0 1", of(INT_VALUE, INT_VALUE), of("0", "1"));
        assertTokenizer("0\t1", of(INT_VALUE, INT_VALUE), of("0", "1"));
        assertTokenizer("0\n1", of(INT_VALUE, INT_VALUE), of("0", "1"));
        assertTokenizer("0\r\n1", of(INT_VALUE, INT_VALUE), of("0", "1"));
        assertTokenizer("0\r1", of(INT_VALUE, INT_VALUE), of("0", "1"));
        assertTokenizer("0 \n 1", of(INT_VALUE, INT_VALUE), of("0", "1"));
        assertTokenizer("0 #foo\n 1", of(INT_VALUE, INT_VALUE), of("0", "1"));
        assertTokenizer("0,1", of(INT_VALUE, INT_VALUE), of("0", "1"));
    }

    private void assertTokenizer(String input, List<Tokenizer.Type> expectedTypes, List<String> expectedTokenValues) {
        assertTokenizer(input, expectedTypes, expectedTokenValues, false);
    }

    /**
     * @param input the input to parse
     * @param expectedTypes list of token types to expect. Pass {@code null} as token type to signal that the tokenizer
     *                      should throw an error on this token.
     * @param expectedTokenValues list of token values to expect.
     * @param expectError whether to expect an error at the end of the expected token list
     */
    private void assertTokenizer(String input, List<Tokenizer.Type> expectedTypes, List<String> expectedTokenValues, boolean expectError) {
        Tokenizer t = new Tokenizer(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        for (int p = 0; ; p++) {
            boolean hasNextToken = false;
            try {
                hasNextToken = t.advance();
            } catch (IOException | IllegalStateException | ParsingException e) {
                if (expectedTypes.size() == p && expectError)
                    return; // successfully catched error
                throw new RuntimeException(e);
            }
            assertEquals(expectedTypes.size() > p, hasNextToken);
            if (!hasNextToken)
                break;
            assertEquals(expectedTypes.get(p), t.type());
            switch (expectedTypes.get(p)) {
                case STRING_VALUE:
                case NAME:
                    assertEquals(expectedTokenValues.get(p), t.string());
                    break;
                case PUNCTUATOR:
                    assertEquals(expectedTokenValues.get(p), "" + (char)t.punctuator());
                    break;
                case INT_VALUE:
                    assertEquals(Integer.parseInt(expectedTokenValues.get(p)), t.integer());
                    break;
                case FLOAT_VALUE:
                    assertEquals(Double.parseDouble(expectedTokenValues.get(p)), t.float_());
                    break;
            }
        }
        if (expectError)
            throw new RuntimeException("No error thrown");
    }
}
