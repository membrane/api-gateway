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

import com.predic8.membrane.core.lang.spel.ExchangeEvaluationContext;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.IntStream;

import static java.lang.reflect.Modifier.isPublic;
import static java.util.Arrays.stream;
import static java.util.List.of;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.springframework.core.ResolvableType.forClass;

public class ReflectiveMethodHandler {

    private final List<Method> storedMethods;

    /**
     * Initializes the ReflectiveMethodHandler with all public methods from the given class.
     */
    public ReflectiveMethodHandler(Class<?> clazz) {
        storedMethods = stream(clazz.getMethods())
                .filter(mth -> isPublic(mth.getModifiers()))
                .toList();
    }

    static TypeDescriptor getTypeDescriptor(Class<?> type) {
        return new TypeDescriptor(forClass(type), type, null);
    }

    /**
     * Calls a previously stored method.
     */
    public TypedValue invokeFunction(EvaluationContext ctx, String func, List<TypeDescriptor> types, Object... args) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        return new TypedValue(getFunction(func, getParameterTypeDescriptors(types)).invoke(null, (getParameters(ctx, args))));
    }

    private static ArrayList<TypeDescriptor> getParameterTypeDescriptors(List<TypeDescriptor> types) {
        return new ArrayList<>(types) {{
            add(getTypeDescriptor(ExchangeEvaluationContext.class));
        }};
    }

    private static Object[] getParameters(EvaluationContext ctx, Object[] args) {
        return new ArrayList<>(of(args)) {{
            add(ctx);
        }}.toArray();
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