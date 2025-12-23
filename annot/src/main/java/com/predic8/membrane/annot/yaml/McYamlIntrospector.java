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

import com.predic8.membrane.annot.*;
import org.jetbrains.annotations.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.*;

import static java.lang.Character.*;
import static java.util.Arrays.*;
import static org.springframework.core.annotation.AnnotationUtils.*;

public final class McYamlIntrospector {

    public static boolean isNoEnvelope(Class<?> clazz) {
        MCElement annotation = clazz.getAnnotation(MCElement.class);
        return annotation != null && annotation.noEnvelope();
    }

    public static boolean isSetter(Method method) {
        return method.getName().startsWith("set") && method.getParameterCount() == 1;
    }

    public static boolean isStructured(Method method) {
        return findAnnotation(method, MCChildElement.class) != null;
    }

    public static boolean matchesJsonKey(Method method, String key) {
        return matchesJsonChildElementKey(method, key)
               || equalsTextContent(method, key)
               || equalsAttributeName(method, key);
    }

    private static boolean matchesJsonChildElementKey(Method method, String key) {
        return findAnnotation(method, MCChildElement.class) != null
               && matchesPropertyName(method, key);
    }

    private static boolean equalsTextContent(Method method, String key) {
        return findAnnotation(method, MCTextContent.class) != null && matchesPropertyName(method, key);
    }

    private static boolean equalsAttributeName(Method method, String key) {
        MCAttribute annotation = findAnnotation(method, MCAttribute.class);
        if (annotation == null)
            return false;
        return matchesPropertyName(method, key) && "".equals(annotation.attributeName())
               || annotation.attributeName().equals(key);
    }

    /**
     * If key is "foo", then method name matches "setFoo", "getFoo".
     *
     * @param method       Method to check.
     * @param propertyName Property name to check.
     */
    private static boolean matchesPropertyName(Method method, String propertyName) {
        return method.getName().substring(3).equalsIgnoreCase(propertyName);
    }

    /**
     * Returns the single {@code @MCChildElement} setter for a class annotated with
     * {@code @MCElement(noEnvelope=true)}.
     * <ul>
     *   <li>Class must be {@code noEnvelope=true}.</li>
     *   <li>No {@code @MCAttribute} setters are allowed.</li>
     *   <li>Exactly one child setter must exist and it must accept a {@link java.util.Collection}.</li>
     * </ul>
     */
    public static <T> Method getSingleChildSetter(Class<T> clazz) {
        MCElement annotation = clazz.getAnnotation(MCElement.class);
        if (annotation == null || !annotation.noEnvelope()) {
            throw new RuntimeException("Class " + clazz.getName() + " has properties, and is not a list.");
        }
        guardHasMCAttributeSetters(clazz);
        Method setter = getChildSetters(clazz).getFirst();
        Class<?> paramType = setter.getParameterTypes()[0];
        if (!java.util.Collection.class.isAssignableFrom(paramType)) {
            throw new RuntimeException("The single @MCChildElement setter in " + clazz.getName() +
                                       " must accept a Collection/List when noEnvelope=true, but found: " + paramType.getName());
        }
        return setter;
    }

    private static <T> @NotNull List<Method> getChildSetters(Class<T> clazz) {
        List<Method> childSetters = stream(clazz.getMethods())
                .filter(McYamlIntrospector::isSetter)
                .filter(method -> findAnnotation(method, MCChildElement.class) != null)
                .toList();
        if (childSetters.isEmpty()) {
            throw new RuntimeException("No @MCChildElement setter found in " + clazz.getName());
        }
        if (childSetters.size() > 1) {
            throw new RuntimeException("Multiple @MCChildElement setters found in " + clazz.getName() + ". Only one is allowed when noEnvelope=true.");
        }
        return childSetters;
    }

    private static <T> void guardHasMCAttributeSetters(Class<T> clazz) {
        if (stream(clazz.getMethods())
                .filter(McYamlIntrospector::isSetter)
                .anyMatch(method -> findAnnotation(method, MCAttribute.class) != null)) {
            throw new RuntimeException("Class " + clazz.getName() + " should not have any @MCAttribute setters, because it is a @MCElement with noEnvelope=true .");
        }
    }

    public static <T> Method findSetterForKey(Class<T> clazz, String key) {
        return stream(clazz.getMethods())
                .filter(McYamlIntrospector::isSetter)
                .filter(method -> matchesJsonKey(method, key))
                .findFirst()
                .orElse(null);
    }

    public static <T> List<Method> findRequiredSetters(Class<T> clazz) {
        return stream(clazz.getMethods())
                .filter(McYamlIntrospector::isSetter)
                .filter(McYamlIntrospector::isRequired)
                .collect(Collectors.toList());
    }

    private static boolean isRequired(Method method) {
        return method.getAnnotation(Required.class) != null;
    }

    public static String getSetterName(Method setter) {
        if (!setter.getName().startsWith("set"))
            throw new IllegalArgumentException("Method is not a setter: " + setter.getName());
        String property = setter.getName().substring(3);
        return toLowerCase(property.charAt(0)) + property.substring(1);
    }

    public static <T> Method getAnySetter(Class<T> clazz) {
        return stream(clazz.getMethods())
                .filter(McYamlIntrospector::isSetter)
                .filter(method -> findAnnotation(method, MCOtherAttributes.class) != null)
                .findFirst()
                .orElse(null);
    }

    public static <T> Method getChildSetter(Class<T> clazz, Class<?> valueClass) {
        return stream(clazz.getMethods())
                .filter(McYamlIntrospector::isSetter)
                .filter(McYamlIntrospector::isStructured)
                .filter(method -> method.getParameterTypes().length == 1)
                .filter(method -> method.getParameterTypes()[0].isAssignableFrom(valueClass))
                .reduce((a, b) -> {
                    throw new RuntimeException("Multiple potential setters found on %s for value of type %s".formatted(clazz.getName(), valueClass.getName()));
                })
                .orElseThrow(() -> new RuntimeException("Could not find child setter on %s for value of type %s".formatted(clazz.getName(), valueClass.getName())));
    }

    public static boolean isReferenceAttribute(Method setter) {
        if (findAnnotation(setter, MCAttribute.class) == null)
            return false;
        return findAnnotation(setter.getParameterTypes()[0], MCElement.class) != null;
    }

    public static boolean hasOtherAttributes(Method setter) {
        return findAnnotation(setter, MCOtherAttributes.class) != null;
    }

    public static String getElementName(Class<?> type) {
        MCElement ann = type.getAnnotation(MCElement.class);
        if (ann != null && ann.name() != null && !ann.name().isBlank())
            return ann.name();
        return type.getSimpleName();
    }

}