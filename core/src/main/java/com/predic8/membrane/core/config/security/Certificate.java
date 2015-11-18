package com.predic8.membrane.core.config.security;

import com.google.common.base.Objects;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;

@MCElement(name="certificate", mixed=true)
public class Certificate {

    String content;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Certificate))
            return false;
        Certificate other = (Certificate)obj;
        return Objects.equal(content, other.content);
    }

    public String getContent() {
        return content;
    }

    /**
     * @description The certificate in PEM format.
     */
    @MCTextContent
    public void setContent(String content) {
        this.content = content;
    }
}
