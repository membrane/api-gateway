package com.predic8.membrane.core.lang.spel.functions;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;

import static java.lang.reflect.Modifier.isPublic;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;
import static org.springframework.core.ResolvableType.forClass;

public class ReflectiveMethodHandler {

    private final Map<SimpleEntry<String, List<TypeDescriptor>>, Method> storedMethods;

    /**
     *
     * */
    public ReflectiveMethodHandler(Class<?> clazz) {
        storedMethods = stream(clazz.getMethods())
                .filter(mth -> isPublic(mth.getModifiers()))
                .collect(toMap(ReflectiveMethodHandler::getMethodKey, m -> m));
    }

    static SimpleEntry<String, List<TypeDescriptor>> getMethodKey(Method m) {
        return new SimpleEntry<>(m.getName(), stream(m.getParameterTypes())
                .map(type -> new TypeDescriptor(forClass(type), type, null))
                .toList());
    }

    public TypedValue invokeFunction(EvaluationContext ctx, String func, List<TypeDescriptor> types, Object... args) throws InvocationTargetException, IllegalAccessException {
        Method function = storedMethods.get(new SimpleEntry<>(func, types));
        return new TypedValue(function.invoke(null, args));
    }
}