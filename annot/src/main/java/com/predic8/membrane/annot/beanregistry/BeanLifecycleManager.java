package com.predic8.membrane.annot.beanregistry;

import java.lang.reflect.Method;

/**
 * A BeanLifecycleManager supports the init-destroy lifecycle of beans.
 *
 * Beans are inited (@PostConstruct method called) when they are being defined, that is before their instance is
 * published via the registry.
 *
 * Beans are destroyed (@PreDestroy method called) when close() is called on the registry.
 *
 * The registry implements this interface.
 */
public interface BeanLifecycleManager {
    /**
     * Tells the registry that the <code>method</code> should be called on the <code>bean</code> when the registry is
     * closed.
     *
     * The registry should call all pre-destroy-callbacks in <b>reverse</b> order in which they were registered.
     */
    void addPreDestroyCallback(Object bean, Method method);
}
