package com.predic8.membrane.annot.yaml;

import java.io.IOException;

public interface BeanCacheObserver {
    void handleAsynchronousInitializationResult(boolean empty);

    void handleBeanEvent(BeanDefinition bd, Object bean, Object oldBean, WatchAction action) throws IOException;

    boolean isActivatable(BeanDefinition bd);
}
