package com.predic8.membrane.annot.yaml;

import com.predic8.membrane.annot.MCChildElement;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static com.predic8.membrane.annot.yaml.McYamlIntrospector.*;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.findSetterForKey;

public class MethodSetter {

    private Method setter;
    private Class<?> beanClass;

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

    public boolean isStructured() {
        return com.predic8.membrane.annot.yaml.McYamlIntrospector.isStructured(setter);
    }

    public boolean isReferenceAttribute() {
        return com.predic8.membrane.annot.yaml.McYamlIntrospector.isReferenceAttribute(setter);
    }

    public boolean hasOtherAttributes() {
        return com.predic8.membrane.annot.yaml.McYamlIntrospector.hasOtherAttributes(setter);
    }

    public static <T> void setSetter(T instance, Method method, Object value) throws InvocationTargetException, IllegalAccessException {
        method.invoke(instance, value);
    }

    public Method getSetter() {
        return setter;
    }

    public void setSetter(Method setter) {
        this.setter = setter;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public void setBeanClass(Class<?> beanClass) {
        this.beanClass = beanClass;
    }
}
