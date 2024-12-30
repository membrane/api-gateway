/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.misc;

import com.predic8.membrane.core.lang.ExchangeExpression.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.stream.*;

import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static org.junit.jupiter.api.Assertions.*;

class SetHeaderInterceptorSpELTest extends AbstractSetHeaderInterceptorTest {


    @Override
    protected Language getLanguage() {
        return SPEL;
    }

    public static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("42", "42"),
                Arguments.of("string", "string"),
                Arguments.of("-${5 * 5}-", "-25-"),
                Arguments.of("${exchange.request.uri}", "/boo"),
                Arguments.of("${headers.host}", "localhost:8080"),
                Arguments.of("${headers['X-Api-Key']}","31415"),
                Arguments.of("${properties['prop']}","88")
        );
    }

    @ParameterizedTest
    @MethodSource("cases")
    void withoutExpressionOnlyConstant(String expression, String expected) throws Exception {
        extracted(expression, expected);
    }


    @Test
    void computeSingeExpression() {
        assertEquals("24", interceptor.evaluateSingleExpression(exchange,message, "1*2*3*4"));
    }

    // @TODO test accessing non existent property

    @Test
    void accessNonExistingProperty() {
        assertEquals("", interceptor.evaluateSingleExpression(exchange,message, "properties.unknown"));
    }

    @Test
    void computeSingeExpressionWithCtx() {
        assertEquals("Panama", interceptor.evaluateSingleExpression(exchange,message, "properties.bar"));
    }

    @Test
    void computeValue() {
        interceptor.setValue("foo ${properties.bar} baz ${properties.foo} coo ${2*3}/${'a'}");
        assertEquals("foo Panama baz  coo 6/a", interceptor.evaluateExpression(exchange,message));
    }

    @Test
    void getJson() {
        interceptor.setValue("foo ${json['a']} baz");
        assertEquals("foo 5 baz", interceptor.evaluateExpression(exchange,message));
    }

    @Test
    void jsonWrongAttribute() {
        interceptor.setValue("foo ${json['b']} baz");
        assertEquals("foo  baz", interceptor.evaluateExpression(exchange,message));
    }

    @Test
    void jsonPath() throws Exception {
        interceptor.setValue("${jsonPath('$.name')}");
        interceptor.handleRequest(exchange);
        assertEquals("Mango", exchange.getRequest().getHeader().getFirstValue("foo"));
    }

    @Test
    @DisplayName("Only set if the header is absent")
    void onlyIfAbsent() throws Exception {
        exchange.getRequest().getHeader().add("X-FOO", "0");

        interceptor.setName("X-FOO");
        interceptor.setValue("42");
        interceptor.setIfAbsent(true);

        interceptor.handleRequest(exchange);

        assertEquals("0", exchange.getRequest().getHeader().getFirstValue("X-FOO"));
    }

    @Test
    @DisplayName("Only set if the header is absent with different casing")
    void onlyIfAbsentCaseDiff() throws Exception {
        exchange.getRequest().getHeader().add("X-FOO", "0");

        interceptor.setName("x-fOo");
        interceptor.setValue("42");
        interceptor.setIfAbsent(true);

        interceptor.handleRequest(exchange);

        assertEquals("0", exchange.getRequest().getHeader().getFirstValue("x-FoO"));
    }


    @Test
    @DisplayName("Overwrite header when it is not absent")
    void notIfAbsent() throws Exception {
        exchange.getRequest().getHeader().add("X-FOO", "0");

        interceptor.setName("X-FOO");
        interceptor.setValue("42");

        interceptor.handleRequest(exchange);

        assertEquals("42", exchange.getRequest().getHeader().getFirstValue("X-FOO"));
    }

    @Test
    @DisplayName("Overwrite header when it is not absent with different casing")
    void notIfAbsentCaseDiff() throws Exception {
        exchange.getRequest().getHeader().add("X-FOO", "0");

        interceptor.setName("x-fOo");
        interceptor.setValue("42");

        interceptor.handleRequest(exchange);

        assertEquals("42", exchange.getRequest().getHeader().getFirstValue("x-FoO"));
    }
}