package com.predic8.membrane.annot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

public class InMemoryJavaFileObject extends InMemoryFileObject implements JavaFileObject {
    private final static Logger log = LoggerFactory.getLogger(InMemoryJavaFileObject.class);

    public InMemoryJavaFileObject(InMemoryData data, String path, Kind kind) {
        super(data, path, kind);
    }

    @Override
    public Kind getKind() {
        log.debug("getKind() {}", inner.toUri());
        return inner.getKind();
    }

    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        log.debug("isNameCompatible({}, {}) {}", simpleName, kind, inner.toUri());
        return inner.isNameCompatible(simpleName, kind);
    }

    @Override
    public NestingKind getNestingKind() {
        log.debug("getNestingKind() {}", inner.toUri());
        return inner.getNestingKind();
    }

    @Override
    public Modifier getAccessLevel() {
        log.debug("getAccessLevel() {}", inner.toUri());
        return inner.getAccessLevel();
    }
}
