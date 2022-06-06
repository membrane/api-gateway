package com.predic8.membrane.core.config.security.acme;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

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
}
