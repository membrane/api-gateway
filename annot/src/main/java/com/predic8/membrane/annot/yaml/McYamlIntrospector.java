package com.predic8.membrane.annot.yaml;

import com.predic8.membrane.annot.*;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;

public final class McYamlIntrospector {

    public static boolean isNoEnvelope(Class<?> clazz) {
        MCElement annotation = clazz.getAnnotation(MCElement.class);
        return annotation != null && annotation.noEnvelope();
    }

    public static boolean isSetter(Method method) {
        return method.getName().startsWith("set") && method.getParameterCount() == 1;
    }

    public static boolean isStructured(Method method) {
        return AnnotationUtils.findAnnotation(method, MCChildElement.class) != null;
    }

    public static boolean matchesJsonKey(Method method, String key) {
        return matchesJsonChildElementKey(method, key)
                || equalsTextContent(method, key)
                || equalsAttributeName(method, key);
    }

    private static boolean matchesJsonChildElementKey(Method method, String key) {
        return findAnnotation(method, MCChildElement.class) != null
                && method.getName().substring(3).equalsIgnoreCase(key);
    }

    private static boolean equalsTextContent(Method method, String key) {
        return AnnotationUtils.findAnnotation(method, MCTextContent.class) != null && method.getName().substring(3).equalsIgnoreCase(key);
    }

    private static boolean equalsAttributeName(Method method, String key) {
        MCAttribute annotation = findAnnotation(method, MCAttribute.class);
        if (annotation == null)
            return false;
        return method.getName().substring(3).equalsIgnoreCase(key) && "".equals(annotation.attributeName())
                || annotation.attributeName().equals(key);
    }

    public static <T> Method getSingleChildSetter(Class<T> clazz) {
        MCElement annotation = clazz.getAnnotation(MCElement.class);
        if (annotation == null || !annotation.noEnvelope()) {
            throw new RuntimeException("Class " + clazz.getName() + " has properties, and is not a list.");
        }
        if (Arrays.stream(clazz.getMethods())
                .filter(McYamlIntrospector::isSetter)
                .anyMatch(method -> findAnnotation(method, MCAttribute.class) != null)) {
            throw new RuntimeException("Class " + clazz.getName() + " should not have any @MCAttribute setters, because it is a @MCElement with noEnvelope=true .");
        }
        List<Method> childSetters = Arrays.stream(clazz.getMethods())
                .filter(McYamlIntrospector::isSetter)
                .filter(method -> findAnnotation(method, MCChildElement.class) != null)
                .toList();
        if (childSetters.isEmpty()) {
            throw new RuntimeException("No @MCChildElement setter found in " + clazz.getName());
        }
        if (childSetters.size() > 1) {
            throw new RuntimeException("Multiple @MCChildElement setters found in " + clazz.getName() + ". Only one is allowed when noEnvelope=true.");
        }
        Method setter = childSetters.getFirst();
        Class<?> paramType = setter.getParameterTypes()[0];
        if (!java.util.Collection.class.isAssignableFrom(paramType)) {
            throw new RuntimeException("The single @MCChildElement setter in " + clazz.getName() +
                    " must accept a Collection/List when noEnvelope=true, but found: " + paramType.getName());
        }
        return setter;
    }

    public static <T> Method getSetter(Class<T> clazz, String key) {
        return Arrays.stream(clazz.getMethods())
                .filter(McYamlIntrospector::isSetter)
                .filter(method -> matchesJsonKey(method, key))
                .findFirst()
                .orElse(null);
    }

    public static <T> Method getAnySetter(Class<T> clazz) {
        return Arrays.stream(clazz.getMethods())
                .filter(McYamlIntrospector::isSetter)
                .filter(method -> AnnotationUtils.findAnnotation(method, MCOtherAttributes.class) != null)
                .findFirst()
                .orElse(null);
    }

    public static <T> Method getChildSetter(Class<T> clazz, Class<?> valueClass) {
        return Arrays.stream(clazz.getMethods())
                .filter(McYamlIntrospector::isSetter)
                .filter(method -> method.getParameterTypes().length == 1)
                .filter(method -> method.getParameterTypes()[0].isAssignableFrom(valueClass))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find child setter on "
                        + clazz.getName() + " for value of type " + valueClass.getName()));
    }

    public static boolean isReferenceAttribute(Method setter) {
        if (findAnnotation(setter, MCAttribute.class) == null)
            return false;
        return findAnnotation(setter.getParameterTypes()[0], MCElement.class) != null;
    }

    public static boolean hasOtherAttributes(Method setter) {
        return findAnnotation(setter, MCOtherAttributes.class) != null;
    }

}