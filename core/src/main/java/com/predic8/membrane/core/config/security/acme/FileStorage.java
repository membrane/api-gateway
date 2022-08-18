package com.predic8.membrane.core.config.security.acme;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

import java.util.Objects;

@MCElement(name = "fileStorage", topLevel = false)
public class FileStorage implements AcmeSynchronizedStorage {

    String dir;

    public FileStorage() {
        // do nothing
    }

    public FileStorage(String dir) {
        setDir(dir);
    }

    public String getDir() {
        return dir;
    }

    @MCAttribute
    public void setDir(String dir) {
        this.dir = dir;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileStorage that = (FileStorage) o;
        return Objects.equals(dir, that.dir);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dir);
    }
}
