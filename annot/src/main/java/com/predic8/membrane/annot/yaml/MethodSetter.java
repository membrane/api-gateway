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
                beanClass = ctx.k8sHelperGenerator().getLocal(ctx.context(), key);
                if (beanClass == null)
                    beanClass = ctx.k8sHelperGenerator().getElement(key);
                if (beanClass != null)
                    setter = getChildSetter(clazz, beanClass);
            } catch (Exception e) {
                throw new RuntimeException("Can't find method or bean for key: " + key + " in " + clazz.getName(), e); // TODO formated
            }
            if (setter == null)
                setter = getAnySetter(clazz);
            if (beanClass == null && setter == null)
                throw new RuntimeException("Can't find method or bean for key: " + key + " in " + clazz.getName()); // TODO formated
        }
        return new MethodSetter(setter, beanClass);
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
