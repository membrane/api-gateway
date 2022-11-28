package com.predic8.membrane.core.openapi.util;

public class ObjectHolder<T> {

    private T value;

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}
