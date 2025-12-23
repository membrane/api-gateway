package com.predic8.membrane.annot.beanregistry;

import com.predic8.membrane.annot.yaml.WatchAction;

/**
 * Signals that a BeanDefinition has changed (=was added, modified, or deleted).
 */
public record BeanDefinitionChanged(
        WatchAction action,
        BeanDefinition bd) implements ChangeEvent {
}
