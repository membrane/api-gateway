package com.predic8.membrane.core.lang.spel.functions;

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.lang.spel.ExchangeEvaluationContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.TypedValue;

import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.AbstractMap.SimpleEntry;

import static com.predic8.membrane.core.lang.spel.functions.ReflectiveMethodHandler.getMethodKey;
import static com.predic8.membrane.core.lang.spel.functions.ReflectiveMethodHandler.getTypeDescriptor;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReflectiveMethodHandlerTest {

    static final TypeDescriptor INT_TYPE_DESC = getTypeDescriptor(Integer.class);
    static final TypeDescriptor FLOAT_TYPE_DESC = getTypeDescriptor(Float.class);
    static ReflectiveMethodHandler rmh;
    static ExchangeEvaluationContext ctx;

    @BeforeAll
    static void init() throws URISyntaxException {
        rmh = new ReflectiveMethodHandler(TestFunctions.class);
        ctx = new ExchangeEvaluationContext(Request.get("foo").buildExchange());
    }

    @Test
    public void testMethodKey() throws NoSuchMethodException {
        assertEquals(getMethodKey(getMethod("test", ExchangeEvaluationContext.class)),
                new SimpleEntry<>("test", of(getTypeDescriptor(ExchangeEvaluationContext.class))));
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

    private static class TestFunctions{

        public static String test(ExchangeEvaluationContext ctx) {
            return "Hello World!";
        }

        public static String hello(String name, ExchangeEvaluationContext ctx) {
            return "Hello " + name;
        }

        public static Integer add(Integer a, Integer b, ExchangeEvaluationContext ctx) {
            return a + b;
        }

        public static Integer add(Float a, Float b, ExchangeEvaluationContext ctx) {
            return (int) (a + b);
        }

        public static String getRequestUri(ExchangeEvaluationContext ctx) {
            return ctx.getExchange().getRequest().getUri();
        }

    }

    @NotNull
    private static Method getMethod(String name, Class<?> clazz) throws NoSuchMethodException {
        if (clazz == null) return TestFunctions.class.getMethod(name);
        return TestFunctions.class.getMethod(name, clazz);
    }
}