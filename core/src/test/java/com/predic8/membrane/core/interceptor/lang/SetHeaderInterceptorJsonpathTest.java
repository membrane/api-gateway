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

import com.predic8.membrane.core.lang.ExchangeExpression.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.stream.*;

import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.*;

class SetHeaderInterceptorJsonpathTest extends AbstractSetHeaderInterceptorTest {

    @Override
    protected Language getLanguage() {
        return JSONPATH;
    }

    /**
     *
     * @return Stream of Arguments( expected, expression )
     */
    static Stream<Arguments> cases() {
        return Stream.of(
                // Arguments.of(<<expected>>,<<expression>>)
                Arguments.of("42", "42"),
                Arguments.of("string", "string"),
                Arguments.of("Mango","${$.name}"),
                Arguments.of("Name is Mango","Name is ${$.name}"),
                Arguments.of("PRIVATE","${$.tags[0]}"),
                Arguments.of("BUSINESS","${$.tags[1]}"),
                Arguments.of("7","${$.tags[2]}")
                );
    }

    @ParameterizedTest
    @MethodSource("cases")
    void withoutExpressionOnlyConstant(Object expected, String expression) throws Exception {
        extracted(expression, expected);
    }

    @Test
    void jsonPath() throws Exception {
        interceptor.setValue("${$.name}");
        interceptor.init(router);
        interceptor.handleRequest(exchange);
        assertEquals("Mango", getHeader("x-bar"));
    }

    @Test
    @DisplayName("Only set if the header is absent")
    void onlyIfAbsent() throws Exception {
        exchange.getRequest().getHeader().add("X-FOO", "0");
        interceptor.setFieldName("X-FOO");
        interceptor.setValue("42");
        interceptor.setIfAbsent(true);
        interceptor.init(router);
        interceptor.handleRequest(exchange);
        assertEquals("0", getHeader("X-FOO"));
    }

    @Test
    @DisplayName("Only set if the header is absent with different casing")
    void onlyIfAbsentCaseDiff() {
        exchange.getRequest().getHeader().add("X-FOO", "0");
        interceptor.setFieldName("x-fOo");
        interceptor.setValue("42");
        interceptor.setIfAbsent(true);
        interceptor.handleRequest(exchange);
        assertEquals("0", getHeader("x-FoO"));
    }

    @Test
    @DisplayName("Overwrite header when it is not absent")
    void notIfAbsent() throws Exception {
        exchange.getRequest().getHeader().add("X-FOO", "0");
        interceptor.setFieldName("X-FOO");
        interceptor.setValue("42");
        interceptor.init(router);
        interceptor.handleRequest(exchange);
        assertEquals("42", getHeader("X-FOO"));
    }

    @Test
    @DisplayName("Overwrite header when it is not absent with different casing")
    void notIfAbsentCaseDiff() throws Exception {
        exchange.getRequest().getHeader().add("X-FOO", "0");
        interceptor.setFieldName("x-fOo");
        interceptor.setValue("42");
        interceptor.init(router);
        interceptor.handleRequest(exchange);
        assertEquals("42", getHeader("x-FoO"));
    }
}