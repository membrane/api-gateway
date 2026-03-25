package com.predic8.membrane.annot.beanregistry;

public interface BeanDefinitionAware {
    void setBeanDefinition(BeanDefinition beanDefinition);

    BeanDefinition getBeanDefinition();
}
