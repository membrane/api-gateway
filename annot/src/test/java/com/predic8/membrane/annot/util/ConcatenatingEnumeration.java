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
