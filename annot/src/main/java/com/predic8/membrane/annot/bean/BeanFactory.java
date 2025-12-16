package com.predic8.membrane.annot.bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.annot.yaml.BeanRegistry;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.*;
import java.util.*;

/**
 * Builds Java objects from a "bean" JSON node.
 */
public final class BeanFactory {

    private final BeanRegistry registry;

    public BeanFactory(BeanRegistry registry) {
        this.registry = registry;
    }

    /**
     * Creates an instance described by the given bean node.
     */
    public Object createFromNode(JsonNode beanBody) {
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

    // TODO keep this? Currently only used for YAMLBeanParsingTest
    private Class<?> loadBeanClass(String className) throws ClassNotFoundException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader != null) {
            try { return Class.forName(className, true, classLoader); } catch (ClassNotFoundException ignored) {}
        }

        ClassLoader grammarClassLoader = registry.getGrammar().getClass().getClassLoader();
        if (grammarClassLoader != null) {
            try { return Class.forName(className, true, grammarClassLoader); } catch (ClassNotFoundException ignored) {}
        }

        ClassLoader beanFactoryClassLoader = BeanFactory.class.getClassLoader();
        if (beanFactoryClassLoader != null) {
            try { return Class.forName(className, true, beanFactoryClassLoader); } catch (ClassNotFoundException ignored) {}
        }

        return Class.forName(className);
    }

    private record ConstructorArg(String value, String ref) {}
    private record Property(String name, String value, String ref) {}

    private List<ConstructorArg> parseConstructorArgList(JsonNode arr) {
        if (!arr.isArray()) return List.of();
        List<ConstructorArg> res = new ArrayList<>();
        for (JsonNode item : arr) {
            JsonNode body = item.isObject() && item.has("constructorArg") ? item.get("constructorArg") : item;
            res.add(new ConstructorArg(getTextOrNull(body, "value"), getTextOrNull(body, "ref")));
        }
        return res;
    }

    private List<Property> parsePropertyList(JsonNode arr) {
        if (!arr.isArray()) return List.of();
        List<Property> res = new ArrayList<>();
        for (JsonNode item : arr) {
            JsonNode body = item.isObject() && item.has("property") ? item.get("property") : item;
            res.add(new Property(getTextContent(body, "name"), getTextOrNull(body, "value"), getTextOrNull(body, "ref")));
        }
        return res;
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

        List<Constructor<?>> constructors = new ArrayList<>();
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
            throw new IllegalArgumentException("No matching constructor found for " + type.getName()
                    + " with " + n + " argument(s).");
        }

        best.setAccessible(true);
        return best.newInstance(bestArgs);
    }

    private Object[] tryResolveCtorArgs(Class<?>[] paramTypes, List<ConstructorArg> args) {
        try {
            Object[] resolved = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                resolved[i] = resolveValueOrRef(paramTypes[i], args.get(i).value(), args.get(i).ref());
            }
            return resolved;
        } catch (Exception e) {
            return null; // not compatible
        }
    }

    private void applyProperties(Object target, List<Property> props) throws Exception {
        if (props == null) return;

        for (Property p : props) {
            String name = p.name();
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("Property name must not be blank.");

            Method setter = findSetter(target.getClass(), name);
            if (setter != null) {
                Class<?> pt = setter.getParameterTypes()[0];
                setter.setAccessible(true);
                setter.invoke(target, resolveValueOrRef(pt, p.value(), p.ref()));
                continue;
            }

            Field f = findField(target.getClass(), name);
            if (f != null) {
                f.setAccessible(true);
                f.set(target, resolveValueOrRef(f.getType(), p.value(), p.ref()));
                continue;
            }

            throw new IllegalArgumentException("No setter/field found for property '" + name
                    + "' on " + target.getClass().getName());
        }
    }

    private Method findSetter(Class<?> clazz, String prop) {
        String setterName = getSetterName(prop);
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) return method;
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) return method;
        }
        return null;
    }

    // e.g. bar -> setBar
    private static @NotNull String getSetterName(String prop) {
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
            Object o = registry.resolveReference(ref);
            if (o != null && !targetType.isInstance(o) && !(targetType.isPrimitive() && isWrapperOfPrimitive(targetType, o.getClass()))) {
                throw new IllegalArgumentException("Ref '" + ref + "' is not assignable to " + targetType.getName());
            }
            return o;
        }
        return convert(value, targetType);
    }

    /**
     * Converts a string literal to the target Java type.
     */
    private Object convert(String raw, Class<?> targetType) {
        if (targetType == String.class) return raw;
        if (raw == null) {
            if (targetType.isPrimitive())
                throw new IllegalArgumentException("Cannot assign null to primitive " + targetType.getName());
            return null;
        }

        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(raw);
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(raw);
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(raw);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(raw);
        if (targetType == float.class || targetType == Float.class) return Float.parseFloat(raw);
        if (targetType == short.class || targetType == Short.class) return Short.parseShort(raw);
        if (targetType == byte.class || targetType == Byte.class) return Byte.parseByte(raw);
        if (targetType == char.class || targetType == Character.class) {
            if (raw.length() != 1) throw new IllegalArgumentException("Expected single character, got: " + raw);
            return raw.charAt(0);
        }

        if (targetType.isEnum()) {
            @SuppressWarnings({"rawtypes","unchecked"})
            Object e = Enum.valueOf((Class<? extends Enum>) targetType, raw);
            return e;
        }

        throw new IllegalArgumentException("Unsupported conversion to " + targetType.getName() + " from value: " + raw);
    }

    private boolean isWrapperOfPrimitive(Class<?> primitive, Class<?> wrapper) {
        return (primitive == int.class && wrapper == Integer.class)
                || (primitive == long.class && wrapper == Long.class)
                || (primitive == boolean.class && wrapper == Boolean.class)
                || (primitive == double.class && wrapper == Double.class)
                || (primitive == float.class && wrapper == Float.class)
                || (primitive == short.class && wrapper == Short.class)
                || (primitive == byte.class && wrapper == Byte.class)
                || (primitive == char.class && wrapper == Character.class);
    }
}
