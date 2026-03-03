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

package com.predic8.membrane.core.lang;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.TemplateExchangeExpression.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static com.predic8.membrane.core.lang.TemplateExpressionParser.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TemplateExpressionParser}.
 * <p>
 * Focus: correct tokenization boundaries (especially nested braces and quotes).
 * We do not verify expression evaluation here, only parsing output shape and text segments.
 */
class TemplateExpressionParserTest {

    private final Interceptor interceptor = mock(Interceptor.class);

    @Test
    void parsesPlainTextOnly() {
        List<TemplateExchangeExpression.Token> tokens =
                parseTokens(interceptor, SPEL, "hello");

        assertEquals(1, tokens.size());
        assertInstanceOf(Text.class, tokens.getFirst());
        assertEquals("hello", tokens.getFirst().getExpression());
    }

    @Test
    void parsesSingleExpressionOnly() {
        var tokens = parseTokens(interceptor, SPEL, "${a}");

        assertEquals(1, tokens.size());
        assertInstanceOf(Expression.class, tokens.getFirst());
        assertEquals("a", tokens.getFirst().getExpression());
    }

    @Test
    void parsesTextExpressionText() {
        var tokens = parseTokens(interceptor, SPEL, "a ${b} c");

        assertEquals(3, tokens.size());
        assertInstanceOf(Text.class, tokens.get(0));
        assertInstanceOf(Expression.class, tokens.get(1));
        assertInstanceOf(Text.class, tokens.get(2));

        assertEquals("a ", tokens.get(0).getExpression());
        assertEquals("b", tokens.get(1).getExpression());
        assertEquals(" c", tokens.get(2).getExpression());
    }

    @Test
    void parsesMultipleExpressions() {
        List<TemplateExchangeExpression.Token> tokens =
                parseTokens(interceptor, SPEL, "a ${b} c ${d} e");

        assertEquals(5, tokens.size());
        assertEquals("a ", tokens.get(0).getExpression());
        assertEquals("b", tokens.get(1).getExpression());
        assertEquals(" c ", tokens.get(2).getExpression());
        assertEquals("d", tokens.get(3).getExpression());
        assertEquals(" e", tokens.get(4).getExpression());
    }

    @Test
    void parsesExpressionWithNestedBracesArrayInitializer() {
        // Regression for: ${new String[]{'a','b','c'}[1]}
        List<TemplateExchangeExpression.Token> tokens =
                parseTokens(
                        interceptor,
                        SPEL,
                        "\"array\": ${new String[]{'a','b','c'}[1]}"
                );

        assertEquals(2, tokens.size());
        assertInstanceOf(Text.class, tokens.get(0));
        assertInstanceOf(Expression.class, tokens.get(1));

        assertEquals("\"array\": ", tokens.get(0).getExpression());
        assertEquals("new String[]{'a','b','c'}[1]", tokens.get(1).getExpression());
    }

    @Test
    void ignoresBracesInsideSingleQuotedStringLiteral() {
        List<TemplateExchangeExpression.Token> tokens =
                parseTokens(
                        interceptor,
                        SPEL,
                        "x ${'a{b}c'} y"
                );

        assertEquals(3, tokens.size());
        assertEquals("x ", tokens.get(0).getExpression());
        assertEquals("'a{b}c'", tokens.get(1).getExpression());
        assertEquals(" y", tokens.get(2).getExpression());
    }

    @Test
    void ignoresBracesInsideDoubleQuotedStringLiteral() {
        List<TemplateExchangeExpression.Token> tokens =
                parseTokens(
                        interceptor,
                        SPEL,
                        "x ${\"a{b}c\"} y"
                );

        assertEquals(3, tokens.size());
        assertEquals("x ", tokens.get(0).getExpression());
        assertEquals("\"a{b}c\"", tokens.get(1).getExpression());
        assertEquals(" y", tokens.get(2).getExpression());
    }

    @Test
    void supportsSpelSingleQuoteEscapingWithDoubleSingleQuote() {
        // SpEL: '' inside single-quoted string represents a literal '
        List<TemplateExchangeExpression.Token> tokens =
                parseTokens(
                        interceptor,
                        SPEL,
                        "x ${'it''s {fine}'} y"
                );

        assertEquals(3, tokens.size());
        assertEquals("x ", tokens.get(0).getExpression());
        assertEquals("'it''s {fine}'", tokens.get(1).getExpression());
        assertEquals(" y", tokens.get(2).getExpression());
    }

    @Test
    void throwsOnUnclosedExpression() {
        var ex = assertThrows(
                ExchangeExpressionException.class,
                () -> parseTokens(interceptor, SPEL, "x ${a y")
        );
        assertTrue(ex.getMessage().contains("Unclosed ${...}"));
    }

    @Test
    void treatsDollarWithoutBraceAsPlainText() {
        List<TemplateExchangeExpression.Token> tokens =
                parseTokens(interceptor, SPEL, "x $ y");

        assertEquals(1, tokens.size());
        assertEquals("x $ y", tokens.getFirst().getExpression());
    }

    @Test
    void parsesAdjacentExpressions() {
        var tokens = parseTokens(interceptor, SPEL, "${a}${b}");

        assertEquals(2, tokens.size());
        assertEquals("a", tokens.get(0).getExpression());
        assertEquals("b", tokens.get(1).getExpression());
    }
}