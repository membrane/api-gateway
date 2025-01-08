/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.lang.spel.functions;

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.lang.spel.SpELExchangeEvaluationContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.TypedValue;

import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static com.predic8.membrane.core.lang.spel.functions.ReflectiveMethodHandler.getTypeDescriptor;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.*;

public class ReflectiveMethodHandlerTest {

    static final TypeDescriptor INT_TYPE_DESC = getTypeDescriptor(Integer.class);
    static final TypeDescriptor FLOAT_TYPE_DESC = getTypeDescriptor(Float.class);
    static final TypeDescriptor CONTEXT_DESC = getTypeDescriptor(SpELExchangeEvaluationContext.class);
    static ReflectiveMethodHandler rmh;
    static SpELExchangeEvaluationContext ctx;

    @BeforeAll
    static void init() throws URISyntaxException {
        rmh = new ReflectiveMethodHandler(TestFunctions.class);
        ctx = new SpELExchangeEvaluationContext(Request.get("foo").buildExchange());
    }

    @Test
    void testHelloWorld() throws Exception {
        assertEquals(
                new TypedValue("Hello World!"),
                rmh.invokeFunction(ctx, "test", of())
                );
    }

    @Test
    void testParam() throws Exception {
        assertEquals(
                new TypedValue("Hello World!"),
                rmh.invokeFunction(ctx, "hello", of(getTypeDescriptor(String.class)), "World!")
                );
    }

    @Test
    void testTwoParams() throws Exception {
        assertEquals(
                new TypedValue(3),
                rmh.invokeFunction(ctx, "add", of(INT_TYPE_DESC, INT_TYPE_DESC), 1, 2)
                );
    }

    @Test
    void testOverload() throws Exception {
        assertEquals(
                new TypedValue(1),
                rmh.invokeFunction(ctx, "add", of(FLOAT_TYPE_DESC, FLOAT_TYPE_DESC), 0.5f, 0.5f)
        );
    }

    @Test
    void testCtx() throws Exception {
        assertEquals(
                new TypedValue("foo"),
                rmh.invokeFunction(ctx, "getRequestUri", of())
               );
    }

    @Test
    void testGetExistingFunction() throws NoSuchMethodException {
        assertEquals(
                getMethod("add", Float.class, Float.class, SpELExchangeEvaluationContext.class),
                rmh.getFunction("add", of(FLOAT_TYPE_DESC, FLOAT_TYPE_DESC, CONTEXT_DESC))
        );
    }

    @Test
    void testGetMissingFunction() {
        assertThrows(
                NoSuchMethodException.class, () ->
                rmh.getFunction("subtract", of(FLOAT_TYPE_DESC, FLOAT_TYPE_DESC, CONTEXT_DESC))
        );
    }

    @Test
    void validateTypeSignatures() {
        assertTrue(rmh.validateTypeSignature(of(getTypeDescriptor(String.class)),
                of(getTypeDescriptor(String.class)))
        );
    }

    @Test
    void validateNonMatchingTypeSignatures() {
        assertFalse(rmh.validateTypeSignature(of(getTypeDescriptor(String.class), getTypeDescriptor(Integer.class)),
                of(getTypeDescriptor(String.class)))
        );
    }

    @Test
    void validateSubtypeTypeSignatures() {
        assertTrue(rmh.validateTypeSignature(of(getTypeDescriptor(Map.class)), of(getTypeDescriptor(HashMap.class))));
    }

    private static class TestFunctions{

        public static String test(SpELExchangeEvaluationContext ignored) {
            return "Hello World!";
        }

        public static String hello(String name, SpELExchangeEvaluationContext ignored) {
            return "Hello " + name;
        }

        public static Integer add(Integer a, Integer b, SpELExchangeEvaluationContext ignored) {
            return a + b;
        }

        public static Integer add(Float a, Float b, SpELExchangeEvaluationContext ignored) {
            return (int) (a + b);
        }

        public static String getRequestUri(SpELExchangeEvaluationContext ctx) {
            return ctx.getExchange().getRequest().getUri();
        }

    }

    @NotNull
    private static Method getMethod(String name, Class<?>... clazz) throws NoSuchMethodException {
        if (clazz == null) return TestFunctions.class.getMethod(name);
        return TestFunctions.class.getMethod(name, clazz);
    }
}