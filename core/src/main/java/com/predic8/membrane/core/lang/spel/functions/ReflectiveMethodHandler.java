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

import com.predic8.membrane.core.lang.spel.*;
import org.springframework.core.convert.*;
import org.springframework.expression.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.ArrayList;
import java.util.stream.*;

import static java.lang.reflect.Modifier.*;
import static java.util.Arrays.*;
import static java.util.List.*;
import static org.springframework.core.ResolvableType.*;

public class ReflectiveMethodHandler {

    private final Object target;
    private final List<Method> storedMethods;

    /**
     * Initializes the ReflectiveMethodHandler with all public instance methods
     * from the given target object.
     */
    public ReflectiveMethodHandler(Object target) {
        this.target = target;
        this.storedMethods = stream(target.getClass().getMethods())
                .filter(m -> isPublic(m.getModifiers()))
                .filter(m -> !isStatic(m.getModifiers()))
                .toList();
    }

    static TypeDescriptor getTypeDescriptor(Class<?> type) {
        return new TypeDescriptor(forClass(type), type, null);
    }

    /**
     * Retrieves a previously stored method.
     */
    public Method retrieveFunction(String func, List<TypeDescriptor> types) throws NoSuchMethodException {
        return getFunction(func, getParameterTypeDescriptors(types));
    }

    /**
     * Calls a method on the configured target instance.
     */
    public TypedValue invokeFunction(Method method, EvaluationContext ctx, Object... args)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        return new TypedValue(method.invoke(target, getParameters(ctx, args)));
    }

    private static ArrayList<TypeDescriptor> getParameterTypeDescriptors(List<TypeDescriptor> types) {
        var result = new ArrayList<>(types);
        result.add(getTypeDescriptor(SpELExchangeEvaluationContext.class));
        return result;
    }

    private static Object[] getParameters(EvaluationContext ctx, Object[] args) {
        var result = new ArrayList<>(of(args));
        result.add(ctx);
        return result.toArray();
    }

    Method getFunction(String func, List<TypeDescriptor> t) throws NoSuchMethodException {
        return storedMethods.stream()
                .filter(m ->
                        m.getName().equals(func) &&
                        validateTypeSignature(getTypeDescriptors(m), t)
                ).findFirst().orElseThrow(NoSuchMethodException::new);
    }

    static List<TypeDescriptor> getTypeDescriptors(Method m) {
        return stream(m.getParameterTypes())
                .map(ReflectiveMethodHandler::getTypeDescriptor)
                .toList();
    }

    /**
     * Verifies that all TypeDescriptors match.
     * Takes into account that a 'provided' TypeDescriptor should match a supertype 'template' TypeDescriptor.
     */
    boolean validateTypeSignature(List<TypeDescriptor> template, List<TypeDescriptor> provided) {
        return template.size() == provided.size() &&
               IntStream.range(0, template.size())
                       .allMatch(i -> provided.get(i).isAssignableTo(template.get(i)));
    }
}