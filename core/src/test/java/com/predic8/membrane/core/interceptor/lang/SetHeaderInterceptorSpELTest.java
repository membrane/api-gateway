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

package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.stream.*;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.*;

class SetHeaderInterceptorSpELTest extends AbstractSetHeaderInterceptorTest {

    @Override
    protected Language getLanguage() {
        return SPEL;
    }

    public static Stream<Arguments> cases() {
        // Arguments.of(<<expression>>, <<expected>>)
        return Stream.of(
                Arguments.of("42", "42"),
                Arguments.of("string", "string"),
                Arguments.of("-${5 * 5}-", "-25-"),
                Arguments.of("${exchange.request.uri}", "/boo"),
                Arguments.of("${headers.host}", "localhost:8080"),
                Arguments.of("${headers['X-Api-Key']}","31415"),
                Arguments.of("${properties['prop']}","88"),
                Arguments.of("a${'b'}c${3}d${'e'}","abc3de")
        );
    }

    @ParameterizedTest
    @MethodSource("cases")
    void withoutExpressionOnlyConstant(String expression, String expected) throws Exception {
        extracted(expression, expected);
    }


    @Test
    void computeSingeExpression() throws Exception {
        assertEquals("24", setHeaderEvalGetValue("${1*2*3*4}"));
    }

    @Test
    void computeSingeExpressionWithCtx() throws Exception {
        assertEquals("Panama", setHeaderEvalGetValue("${property.bar}"));
    }

    @Test
    void accessNonExistingProperty() throws Exception {
        assertEquals("", setHeaderEvalGetValue("${properties.unknown}"));
    }

    @Test
    void complex() throws Exception {
        assertEquals("foo Panama baz  coo 6/a", setHeaderEvalGetValue("foo ${properties.bar} baz ${properties.foo} coo ${2*3}/${'a'}"));
    }

    @Test
    void getJson() throws Exception {
        assertEquals("foo 5 baz", setHeaderEvalGetValue("foo ${json['a']} baz"));
    }

    @Test
    void jsonWrongAttribute() throws Exception {
        assertEquals("foo  baz", setHeaderEvalGetValue("foo ${json['b']} baz"));
    }

    @Test
    void jsonPath() throws Exception {
        assertEquals("Mango", setHeaderEvalGetValue("${jsonPath('$.name')}"));
    }

    @Test
    @DisplayName("Only set if the header is absent")
    void onlyIfAbsent() {
        exchange.getRequest().getHeader().add("X-FOO", "0");
        interceptor.setName("X-FOO");
        interceptor.setValue("42");
        interceptor.setIfAbsent(true);
        interceptor.handleRequest(exchange);
        assertEquals("0", getHeader("X-FOO"));
    }

    @Test
    @DisplayName("Only set if the header is absent with different casing")
    void onlyIfAbsentCaseDiff() {
        exchange.getRequest().getHeader().add("X-FOO", "0");
        interceptor.setName("x-fOo");
        interceptor.setValue("42");
        interceptor.setIfAbsent(true);
        interceptor.handleRequest(exchange);
        assertEquals("0", getHeader("x-FoO"));
    }

    @Test
    @DisplayName("Overwrite header when it is not absent")
    void notIfAbsent() throws Exception {
        exchange.getRequest().getHeader().add("X-FOO", "0");
        interceptor.setName("X-FOO");
        interceptor.setValue("42");
        interceptor.setIfAbsent(false);
        interceptor.init(router);
        interceptor.handleRequest(exchange);
        assertEquals("42", getHeader("X-FOO"));
    }

    @Test
    @DisplayName("Do not overwrite existing header.")
    void ifAbsent() throws Exception {
        exchange.getRequest().getHeader().add("X-FOO", "0");
        interceptor.setName("X-FOO");
        interceptor.setValue("42");
        interceptor.setIfAbsent(true);
        interceptor.init(router);
        interceptor.handleRequest(exchange);
        assertEquals("0", getHeader("X-FOO"));
    }

    @Test
    @DisplayName("Overwrite header when it is not absent with different casing")
    void notIfAbsentCaseDiff() throws Exception {
        exchange.getRequest().getHeader().add("X-FOO", "0");
        interceptor.setName("x-fOo");
        interceptor.setValue("42");
        interceptor.init(router);
        interceptor.handleRequest(exchange);
        assertEquals("42", getHeader("x-FoO"));
    }

    @Test
    void failOnErrorTrue() throws Exception {
        interceptor.setValue("42${wrong}");
        interceptor.setFailOnError(true);
        interceptor.init(router);
        Outcome outcome = interceptor.handleRequest(exchange);
        assertEquals(ABORT, outcome);
        assertEquals(null, super.getHeader("x-FoO"));
    }

    @Test
    void failOnErrorFalse() throws Exception {
        interceptor.setValue("42${wrong}");
        interceptor.setFailOnError(false);
        interceptor.init(router);
        Outcome outcome = interceptor.handleRequest(exchange);
        assertEquals(CONTINUE, outcome);
        assertEquals(null, super.getHeader("x-FoO"));
    }

    private String setHeaderEvalGetValue(String expr) throws Exception {
        interceptor.setValue(expr);
        interceptor.init(router);
        interceptor.handleRequest(exchange);
        return exchange.getRequest().getHeader().getFirstValue("x-bar");
    }
}