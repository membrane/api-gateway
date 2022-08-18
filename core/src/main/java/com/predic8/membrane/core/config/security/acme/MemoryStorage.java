package com.predic8.membrane.core.config.security.acme;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

import java.util.Objects;

/**
 * @description
 * For testing purposes only.
 */
@MCElement(name = "memoryStorage", topLevel = false)
public class MemoryStorage implements AcmeSynchronizedStorage {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemoryStorage that = (MemoryStorage) o;
        return true;
    }

    @Override
    public int hashCode() {
        return 14325236;
    }
}
