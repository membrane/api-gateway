package com.predic8.membrane.core.config.security;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;

@MCElement(name="certificate", mixed=true)
public class Certificate {

    String content;

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
