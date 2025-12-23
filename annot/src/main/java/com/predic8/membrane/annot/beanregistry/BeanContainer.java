package com.predic8.membrane.annot.beanregistry;

import com.fasterxml.jackson.databind.JsonNode;

public class BeanContainer {
    private final BeanDefinition definition;
    /**
     * Constructed bean after initialization.
     */
    private volatile Object singleton;

    public BeanContainer(BeanDefinition definition) {
        this.definition = definition;
    }


    public Object getSingleton() {
        return singleton;
    }

    public void setSingleton(Object singleton) {
        this.singleton = singleton;
    }

    public BeanDefinition getDefinition() {
        return definition;
    }
}
