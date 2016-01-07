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
