package com.predic8.membrane.annot.util;

import java.util.Enumeration;

public class ConcatenatingEnumeration<T> implements Enumeration<T> {
    private final T[] additionalElements;
    private final Enumeration<T> base;

    public ConcatenatingEnumeration(T[] additionalElements, Enumeration<T> base) {
        this.additionalElements = additionalElements;
        this.base = base;
    }

    int nextIndex = 0;

    @Override
    public boolean hasMoreElements() {
        if (nextIndex < additionalElements.length) {
            return true;
        }
        return base.hasMoreElements();
    }

    @Override
    public T nextElement() {
        if (nextIndex < additionalElements.length) {
            return additionalElements[nextIndex++];
        }
        return base.nextElement();
    }

}
