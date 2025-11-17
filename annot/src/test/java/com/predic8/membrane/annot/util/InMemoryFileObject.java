package com.predic8.membrane.annot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.*;
import java.net.URI;

public class InMemoryFileObject implements FileObject {
    private static final Logger log = LoggerFactory.getLogger(InMemoryFileObject.class);

    private final InMemoryData data;
    protected final SimpleJavaFileObject inner;

    public InMemoryFileObject(InMemoryData data, String path) {
        this.data = data;
        inner = new InnerFileObject(data, URI.create("string:///" + path), JavaFileObject.Kind.OTHER);
    }

    public InMemoryFileObject(InMemoryData data, String path, JavaFileObject.Kind kind) {
        this.data = data;
        inner = new InnerFileObject(data, URI.create("string:///" + path), kind);
    }

    @Override
    public URI toUri() {
        log.debug("toUri() {}", inner.toUri());
        return inner.toUri();
    }

    @Override
    public String getName() {
        log.debug("getName() {}", inner.toUri());
        return inner.getName();
    }

    @Override
    public InputStream openInputStream() throws IOException {
        log.debug("openInputStream() {}", inner.toUri());
        return inner.openInputStream();
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        log.debug("openOutputStream() {}", inner.toUri());
        return inner.openOutputStream();
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        log.debug("openReader({}) {}", ignoreEncodingErrors, inner.toUri());
        return inner.openReader(ignoreEncodingErrors);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        log.debug("getCharContent({}) {}", ignoreEncodingErrors, inner.toUri());
        return inner.getCharContent(ignoreEncodingErrors);
    }

    @Override
    public Writer openWriter() throws IOException {
        log.debug("openWriter() {}", inner.toUri());
        return inner.openWriter();
    }

    @Override
    public long getLastModified() {
        log.debug("getLastModified() {}", inner.toUri());
        return inner.getLastModified();
    }

    @Override
    public boolean delete() {
        log.debug("delete() {}", inner.toUri());
        return inner.delete();
    }

    @Override
    public String toString() {
        return "InMemoryFileObject[" + inner.toString() + "]";
    }
}
