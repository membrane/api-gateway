package com.predic8.membrane.core.lang.spel.functions;

import com.predic8.membrane.core.lang.spel.ExchangeEvaluationContext;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.reflect.Modifier.isPublic;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;
import static org.springframework.core.ResolvableType.forClass;

public class ReflectiveMethodHandler {

    private final Map<SimpleEntry<String, List<TypeDescriptor>>, Method> storedMethods;

    /**
     * Initializes the ReflectiveMethodHandler with all public methods from the given class.
     */
    public ReflectiveMethodHandler(Class<?> clazz) {
        storedMethods = stream(clazz.getMethods())
                .filter(mth -> isPublic(mth.getModifiers()))
                .collect(toMap(ReflectiveMethodHandler::getMethodKey, m -> m));
    }

    /**
     * Generates a SimpleEntry object representing the method signature of the given method.
     */
    static SimpleEntry<String, List<TypeDescriptor>> getMethodKey(Method m) {
        return new SimpleEntry<>(m.getName(), stream(m.getParameterTypes())
                .map(ReflectiveMethodHandler::getTypeDescriptor)
                .toList());
    }

    static TypeDescriptor getTypeDescriptor(Class<?> type) {
        return new TypeDescriptor(forClass(type), type, null);
    }

    /**
     * Calls a previously stored method, utilizing the SimpleEntry object as key.
     */
    public TypedValue invokeFunction(EvaluationContext ctx, String func, List<TypeDescriptor> types, Object... args) throws InvocationTargetException, IllegalAccessException {
        return new TypedValue(getFunction(func, getParameterTypeDescriptors(types)).invoke(null, (getParameters(ctx, args)).toArray()));
    }

    private static ArrayList<TypeDescriptor> getParameterTypeDescriptors(List<TypeDescriptor> types) {
        return new ArrayList<>(types) {{
            add(getTypeDescriptor(ExchangeEvaluationContext.class));
        }};
    }

    // TODO return array
    private static ArrayList<Object> getParameters(EvaluationContext ctx, Object[] args) {
        return new ArrayList<>(List.of(args)) {{
            add(ctx);
        }};
    }

    private Method getFunction(String func, List<TypeDescriptor> t) {
        return storedMethods.get(new SimpleEntry<>(func, t));
    }
}