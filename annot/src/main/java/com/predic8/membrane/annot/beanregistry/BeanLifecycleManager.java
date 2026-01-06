package com.predic8.membrane.annot.beanregistry;

import java.lang.reflect.Method;

public interface BeanLifecycleManager {
    void addPreDestroyCallback(Object bean, Method method);
}
