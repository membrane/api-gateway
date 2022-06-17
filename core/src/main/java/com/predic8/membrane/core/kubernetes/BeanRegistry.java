package com.predic8.membrane.core.kubernetes;

public interface BeanRegistry {
    Object resolveReference(String url);
}
