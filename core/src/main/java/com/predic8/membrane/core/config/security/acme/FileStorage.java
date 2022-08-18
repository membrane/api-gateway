/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
