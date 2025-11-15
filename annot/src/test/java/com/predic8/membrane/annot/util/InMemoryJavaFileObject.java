/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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
