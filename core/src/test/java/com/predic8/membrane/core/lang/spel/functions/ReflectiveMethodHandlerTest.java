package com.predic8.membrane.core.lang.spel.functions;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.TypedValue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;

import static com.predic8.membrane.core.lang.spel.functions.ReflectiveMethodHandler.getMethodKey;
import static com.predic8.membrane.core.lang.spel.functions.ReflectiveMethodHandler.getTypeDescriptor;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReflectiveMethodHandlerTest {

    static final TypeDescriptor INT_TYPE_DESC = getTypeDescriptor(Integer.class);

    @Test
    public void testMethodKey() throws NoSuchMethodException {
        assertEquals(getMethodKey(getMethod("test", null)),
                new SimpleEntry<String, List<TypeDescriptor>>("test", of()));
        assertEquals(getMethodKey(getMethod("hello", String.class)),
                new SimpleEntry<>("hello", of(getTypeDescriptor(String.class))));
    }


    @Test void testMethodHandler() throws InvocationTargetException, IllegalAccessException {
        ReflectiveMethodHandler rmh = new ReflectiveMethodHandler(TestFunctions.class);
        assertEquals(
                rmh.invokeFunction(null, "test", of()),
                new TypedValue("Hello World!"));
        assertEquals(
                rmh.invokeFunction(null, "hello", of(getTypeDescriptor(String.class)), "World!"),
                new TypedValue("Hello World!"));
        assertEquals(
                rmh.invokeFunction(null, "add", of(INT_TYPE_DESC, INT_TYPE_DESC), 1, 2),
                new TypedValue(3));
    }

    private static class TestFunctions{

        public static String test() {
            return "Hello World!";
        }

        public static String hello(String name) {
            return "Hello " + name;
        }

        public static Integer add(Integer a, Integer b) {
            return a + b;
        }


    }

    @NotNull
    private static Method getMethod(String name, Class<?> clazz) throws NoSuchMethodException {
        if (clazz == null) return TestFunctions.class.getMethod(name);
        return TestFunctions.class.getMethod(name, clazz);
    }
}