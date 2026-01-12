/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.bean;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.beanregistry.BeanRegistry;
import com.predic8.membrane.annot.util.*;
import org.jetbrains.annotations.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.annot.util.ReflectionUtil.isWrapperOfPrimitive;

/**
 * Builds Java objects from a "bean" JSON node (YAML).
 */
public final class BeanFactory {

    private final BeanRegistry registry;

    public BeanFactory(BeanRegistry registry) {
        this.registry = registry;
    }

    /**
     * Creates an instance described by the given bean node.
     */
    public Object create(JsonNode beanBody) {
        String className = getTextContent(beanBody, "class");

        try {
            Object instance = instantiate(
                    loadBeanClass(className),
                    parseConstructorArgList(beanBody.path("constructorArgs"))
            );
            applyProperties(instance, parsePropertyList(beanBody.path("properties")));
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Could not create bean for class: " + className, e);
        }
    }

    // TODO simplify this. 'normal' code should not be required to use classloader magic
    private Class<?> loadBeanClass(String className) throws ClassNotFoundException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader != null) {
            try {
                return Class.forName(className, true, classLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        classLoader = registry.getGrammar().getClass().getClassLoader();
        if (classLoader != null) {
            try {
                return Class.forName(className, true, classLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        classLoader = BeanFactory.class.getClassLoader();
        if (classLoader != null) {
            try {
                return Class.forName(className, true, classLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        return Class.forName(className);
    }

    private class ConstructorArg {
        String value, ref;

        public ConstructorArg(JsonNode node) {
            var item = node.isObject() && node.has("constructorArg") ? node.get("constructorArg") : node;

            value = getTextOrNull(item, "value");
            ref = getTextOrNull(item, "ref");
        }
    }

    private class Property {
        String name, value, ref;

        public Property(JsonNode node) {
            var item = node.isObject() && node.has("property") ? node.get("property") : node;

            name = getTextContent(item, "name");
            value = getTextOrNull(item, "value");
            ref = getTextOrNull(item, "ref");
        }

        public boolean isBlank() {
            return name == null || name.isBlank();
        }
    }

    private List<ConstructorArg> parseConstructorArgList(JsonNode arr) {
        if (arr == null || !arr.isArray()) return List.of();

        return StreamSupport.stream(arr.spliterator(), false)
                .map(ConstructorArg::new)
                .toList();
    }

    private List<Property> parsePropertyList(JsonNode arr) {
        if (arr == null || !arr.isArray()) return List.of();

        return StreamSupport.stream(arr.spliterator(), false)
                .map(Property::new)
                .toList();
    }

    private String getTextContent(JsonNode n, String key) {
        JsonNode v = n.get(key);
        if (v == null || !v.isTextual() || v.asText().isBlank())
            throw new IllegalArgumentException("Missing/blank '" + key + "' in bean spec.");
        return v.asText();
    }

    private String getTextOrNull(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v != null && v.isTextual() ? v.asText() : null;
    }

    private Object instantiate(Class<?> type, List<ConstructorArg> args) throws Exception {
        int n = args == null ? 0 : args.size();

        Set<Constructor<?>> constructors = new LinkedHashSet<>();
        constructors.addAll(Arrays.asList(type.getConstructors()));
        constructors.addAll(Arrays.asList(type.getDeclaredConstructors()));

        Constructor<?> best = null;
        Object[] bestArgs = null;

        for (Constructor<?> c : constructors) {
            if (c.getParameterCount() != n) continue;
            Object[] resolved = tryResolveCtorArgs(c.getParameterTypes(), args);
            if (resolved != null) {
                best = c;
                bestArgs = resolved;
                break;
            }
        }

        if (best == null) {
            throw new IllegalArgumentException("No matching constructor found for %s with %d argument(s).".formatted(type.getName(), n));
        }

        best.setAccessible(true);
        return best.newInstance(bestArgs);
    }

    private Object[] tryResolveCtorArgs(Class<?>[] paramTypes, List<ConstructorArg> args) {
        try {
            Object[] resolved = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                resolved[i] = resolveValueOrRef(paramTypes[i], args.get(i).value, args.get(i).ref);
            }
            return resolved;
        } catch (Exception e) {
            return null; // not compatible
        }
    }

    private void applyProperties(Object target, List<Property> props) throws Exception {
        for (Property p : props) {
            if (p.isBlank())
                throw new IllegalArgumentException("Property name must not be blank.");

            Method setter = findSetter(target.getClass(), p.name);
            if (setter != null) {
                Class<?> pt = setter.getParameterTypes()[0];
                setter.setAccessible(true);
                setter.invoke(target, resolveValueOrRef(pt, p.value, p.ref));
                continue;
            }

            Field f = findField(target.getClass(), p.name);
            if (f != null) {
                f.setAccessible(true);
                f.set(target, resolveValueOrRef(f.getType(), p.value, p.ref));
                continue;
            }

            throw new IllegalArgumentException("No setter/field found for property '%s' on %s".formatted(p.name, target.getClass().getName()));
        }
    }

    private Method findSetter(Class<?> clazz, String prop) {
        String setterName = getSetterName(prop);
        for (Method method : clazz.getMethods()) {
            if (matchesSetter(method, setterName)) return method;
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (matchesSetter(method, setterName)) return method;
        }
        return null;
    }

    private static boolean matchesSetter(Method method, String setterName) {
        return method.getName().equals(setterName) && method.getParameterCount() == 1;
    }

    // e.g. bar -> setBar
    private static @NotNull String getSetterName(String prop) {
        if (prop == null || prop.isEmpty()) {
            throw new IllegalArgumentException("Property name cannot be null or empty");
        }
        return "set" + Character.toUpperCase(prop.charAt(0)) + prop.substring(1);
    }

    private Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private Object resolveValueOrRef(Class<?> targetType, String value, String ref) {
        if (ref != null && !ref.isBlank()) {
            Object o = registry.resolve(ref);
            if (o != null && !targetType.isInstance(o)) {
                if (!(targetType.isPrimitive() && isWrapperOfPrimitive(targetType, o.getClass()))) {
                    throw new IllegalArgumentException("Ref '%s' is not assignable to %s".formatted(ref, targetType.getName()));
                }
            }
            return o;
        }
        return ReflectionUtil.convert(value, targetType);
    }
}
