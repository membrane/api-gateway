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

package com.predic8.membrane.annot.yaml;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.MCChildElement;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static com.predic8.membrane.annot.yaml.GenericYamlParser.*;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.*;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.findSetterForKey;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.util.Locale.ROOT;

public class MethodSetter {

    private final Method setter;
    private final Class<?> beanClass;

    public MethodSetter(Method setter, Class<?> beanClass) {
        this.setter = setter;
        this.beanClass = beanClass;
    }

    /**
     * Resolves which setter on {@code clazz} should handle the given YAML field {@code key} and,
     * if needed, which bean class that field represents.
     * Throws a {@link RuntimeException} if neither a matching setter nor a resolvable bean class can be found.
     */
    public static <T> @NotNull MethodSetter getMethodSetter(ParsingContext ctx, Class<T> clazz, String key) {
        Method setter = findSetterForKey(clazz, key);
        // MCChildElements which are not lists are directly declared as beans,
        // their name should be interpreted as an element name
        if (setter != null && setter.getAnnotation(MCChildElement.class) != null) {
            if (!List.class.isAssignableFrom(setter.getParameterTypes()[0]))
                setter = null;
        }
        Class<?> beanClass = null;
        if (setter == null) {
            try {
                beanClass = ctx.grammar().getLocal(ctx.context(), key);
                if (beanClass == null)
                    beanClass = ctx.grammar().getElement(key);
                if (beanClass != null)
                    setter = getChildSetter(clazz, beanClass);
            } catch (Exception e) {
                throw new RuntimeException("Can't find method or bean for key '%s' in %s".formatted(key, clazz.getName()), e);
            }
            if (setter == null)
                setter = getAnySetter(clazz);
            if (beanClass == null && setter == null)
                throw new RuntimeException("Can't find method or bean for key '%s' in %s".formatted(key, clazz.getName()));
        }
        return new MethodSetter(setter, beanClass);
    }

    public Class<?> getParameterType() {
        return setter.getParameterTypes()[0];
    }

    public <T> void setSetter(T instance, ParsingContext ctx, JsonNode node, String key) throws InvocationTargetException, IllegalAccessException, WrongEnumConstantException {
        setter.invoke(instance, resolveSetterValue(ctx, node.get(key), key));
    }

    private Object resolveSetterValue(ParsingContext ctx, JsonNode node, String key) throws WrongEnumConstantException, ParsingException {
        Class<?> wanted = getParameterType();
        if (Collection.class.isAssignableFrom(wanted))
            return parseListIncludingStartEvent(ctx, node);

        if (wanted.isEnum()) return parseEnum(wanted, node);
        if (wanted.equals(String.class)) return node.asText();

        if (wanted == Integer.TYPE || wanted == Integer.class) return parseInt(node.asText());
        if (wanted == Long.TYPE || wanted == Long.class) return parseLong(node.asText());
        if (wanted == Boolean.TYPE || wanted == Boolean.class) return parseBoolean(node.asText());

        if (wanted.equals(Map.class) && McYamlIntrospector.hasOtherAttributes(setter)) return Map.of(key, node.asText());
        if (McYamlIntrospector.isStructured(setter)) {
            if (beanClass != null) return createAndPopulateNode(ctx.updateContext(key), beanClass, node);
            return createAndPopulateNode(ctx.updateContext(key), wanted, node);
        }
        if (McYamlIntrospector.isReferenceAttribute(setter)) return ctx.registry().resolveReference(node.asText());
        throw new RuntimeException("Not implemented setter type " + wanted);
    }

    public Method getSetter() {
        return setter;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    private static <E extends Enum<E>> E parseEnum(Class<?> enumClass, JsonNode node) throws WrongEnumConstantException {
        String value = node.asText().toUpperCase(ROOT);
        @SuppressWarnings("unchecked")
        Class<E> castEnumClass = (Class<E>) enumClass;
        try {
            return Enum.valueOf(castEnumClass, value);
        } catch (IllegalArgumentException e) {
            throw new WrongEnumConstantException(enumClass, value);
        }
    }
}
