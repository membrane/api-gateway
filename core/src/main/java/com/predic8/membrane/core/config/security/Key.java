package com.predic8.membrane.core.config.security;

import com.google.common.base.Objects;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import org.springframework.beans.factory.annotation.Required;

import java.util.ArrayList;
import java.util.List;

/**
 * @description Experimental.
 * <p>Allows to insert a PEM block containing the key (as well as one or more blocks for the
 * certificate(s)) directly into the proxies.xml file.</p>
 * <p>This is an alternative for {@link KeyStore}.</p>
 */
@MCElement(name="key")
public class Key {
    @MCElement(name="private", mixed=true)
    public static class Private {
        String content;

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Private))
                return false;
            Private other = (Private)obj;
            return Objects.equal(content, other.content);
        }

        public String getContent() {
            return content;
        }
        /**
         * @description The key in PEM format.
         */
        @MCTextContent
        public void setContent(String content) {
            this.content = content;
        }
    }

    String password;
    Private private_;
    List<Certificate> certificates = new ArrayList<Certificate>();

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Key))
            return false;
        Key other = (Key)obj;
        return Objects.equal(password, other.password)
                && Objects.equal(private_, other.private_)
                && Objects.equal(certificates, other.certificates);
    }

    public String getPassword() {
        return password;
    }
    @MCAttribute
    public void setPassword(String password) {
        this.password = password;
    }

    public Private getPrivate() {
        return private_;
    }
    @Required
    @MCChildElement(order=1)
    public void setPrivate(Private private_) {
        this.private_ = private_;
    }

    public List<Certificate> getCertificates() {
        return certificates;
    }
    @Required
    @MCChildElement(order=2)
    public void setCertificates(List<Certificate> certificates) {
        this.certificates = certificates;
    }
}
