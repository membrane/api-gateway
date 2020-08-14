/* Copyright 2016 predic8 GmbH, www.predic8.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. */

package com.predic8.membrane.core.config.security;

import com.google.common.base.Objects;
import com.predic8.io.IOUtil;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.util.ByteUtil;

import java.io.IOException;

public abstract class Blob {
    String content;
    String location;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Blob))
            return false;
        Blob other = (Blob)obj;
        return Objects.equal(content, other.content)
                && Objects.equal(location, other.location);
    }

    public String getContent() {
        return content;
    }
    @MCTextContent
    public void setContent(String content) {
        this.content = content;
    }

    public String getLocation() {
        return location;
    }
    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public String get(ResolverMap resolverMap, String baseLocation) throws IOException {
        if (getLocation() != null) {
            if (getContent() != null && getContent().length() > 0)
                throw new IllegalStateException("On <"+getName()+">, ./text() and ./@location cannot be set at the same time.");
            return new String(ByteUtil.getByteArrayData(resolverMap.resolve(ResolverMap.combine(baseLocation, getLocation()))));
        } else {
            if (getContent() == null)
                throw new IllegalStateException("On <"+getName()+">, either ./text() or ./@location must be set.");
            return getContent();
        }
    }

    /**
     * The name of this XML element.
     */
    private String getName() {
        return getClass().getAnnotation(MCElement.class).name();
    }
}
