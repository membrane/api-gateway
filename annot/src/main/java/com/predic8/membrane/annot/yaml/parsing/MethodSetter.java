/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.yaml.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.annot.yaml.McYamlIntrospector;
import com.predic8.membrane.annot.yaml.ParsingContext;
import com.predic8.membrane.annot.yaml.WrongEnumConstantException;
import com.predic8.membrane.annot.yaml.parsing.binding.CollectionBinder;
import com.predic8.membrane.annot.yaml.parsing.binding.ObjectBinder;
import com.predic8.membrane.annot.yaml.parsing.binding.ScalarValueConverter;
import com.predic8.membrane.annot.yaml.parsing.binding.SetterResolver;
import com.predic8.membrane.annot.yaml.parsing.binding.SetterResolver.ResolvedSetter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.Collection;
import java.util.List;

public class MethodSetter {

    private static final SetterResolver SETTER_RESOLVER = new SetterResolver();

    private final Method setter;
    private final Class<?> beanClass;
    private final ScalarValueConverter scalarValueConverter = new ScalarValueConverter();

    public MethodSetter(Method setter, Class<?> beanClass) {
        this.setter = setter;
        this.beanClass = beanClass;
    }

    /**
     * Resolves which setter on {@code clazz} should handle the given YAML field {@code key} and,
     * if needed, which bean class that field represents.
     * Throws a {@link RuntimeException} if neither a matching setter nor a resolvable bean class can be found.
     *
     * @param clazz Searches for a setter on this class.
     * @param key   Searches for a setter with this name.
     */
    public static <T> @NotNull MethodSetter getMethodSetter(ParsingContext<?> ctx, Class<T> clazz, String key) {
        ResolvedSetter resolvedSetter = SETTER_RESOLVER.resolve(ctx, clazz, key);
        return new MethodSetter(resolvedSetter.setter(), resolvedSetter.beanType());
    }

    public Class<?> getParameterType() {
        return setter.getParameterTypes()[0];
    }

    public <T> void setSetter(T instance, ParsingContext<?> ctx, JsonNode node, String key) throws InvocationTargetException, IllegalAccessException, WrongEnumConstantException {
        setter.invoke(instance, resolveSetterValue(ctx, node.get(key), key));
    }

    private Object resolveSetterValue(ParsingContext<?> ctx, JsonNode node, String key) throws WrongEnumConstantException, ConfigurationParsingException {
        Class<?> wanted = getParameterType();

        // Collections / repeated elements
        List<Object> list = getObjectList(ctx, node, key, wanted);
        if (list != null) return list;

        // Structured objects
        if (McYamlIntrospector.isStructured(setter)) {
            if (beanClass != null)
                return ObjectBinder.bind(ctx.updateContext(key).addPath("." + key), beanClass, node);
            return ObjectBinder.bind(ctx.updateContext(key).addPath("." + key), wanted, node);
        }

        return coerceScalarOrReference(ctx, node, key, wanted);
    }

    /**
     * Attempts to coerce a given JSON node into the desired scalar, enum, reference, or map type,
     * as specified by the provided target class.
     *
     * @param ctx    The parsing context, providing access to type resolution and bean lookup mechanisms.
     * @param node   The JSON node to be coerced into the desired type.
     * @param key    The key corresponding to the JSON node, often used for error messages or map assignments.
     * @param wanted The target class specifying the type into which the node should be converted.
     * @return The coerced object, matching the desired type, derived from the input node.
     * @throws WrongEnumConstantException    If the node value does not match any of the constants in the enum type.
     * @throws ConfigurationParsingException If the provided type is unsupported for coercion or other unexpected issues arise.
     */
    Object coerceScalarOrReference(ParsingContext<?> ctx, JsonNode node, String key, Class<?> wanted) throws WrongEnumConstantException {
        return scalarValueConverter.coerceScalarOrReference(ctx, setter, node, key, wanted);
    }

    private @Nullable List<Object> getObjectList(ParsingContext<?> ctx, JsonNode node, String key, Class<?> wanted) {
        if (!Collection.class.isAssignableFrom(wanted))
            return null;

        Class<?> elemType = getCollectionElementType(setter);
        List<Object> list = CollectionBinder.parseListIncludingStartEvent(ctx.addPath("." + key), node, elemType);
        if (elemType != null) {
            for (Object o : list) {
                if (o == null) continue;
                if (!elemType.isAssignableFrom(o.getClass())) {
                    throw new ConfigurationParsingException("Value of type '%s' is not allowed in list '%s'. Expected '%s'."
                            .formatted(McYamlIntrospector.getElementName(o.getClass()), key, elemType.getSimpleName()));
                }
            }
        }
        return list;
    }

    public Method getSetter() {
        return setter;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public static Class<?> getCollectionElementType(Method setter) {
        Type t = setter.getGenericParameterTypes()[0];
        if (!(t instanceof ParameterizedType pt)) return null;
        Type arg = pt.getActualTypeArguments()[0];
        if (arg instanceof Class<?> c) return c;
        if (arg instanceof WildcardType wt) {
            Type[] upper = wt.getUpperBounds();
            if (upper.length == 1 && upper[0] instanceof Class<?> uc) return uc;
        }
        if (arg instanceof ParameterizedType p2 && p2.getRawType() instanceof Class<?> rc) return rc;
        return null;
    }
}
