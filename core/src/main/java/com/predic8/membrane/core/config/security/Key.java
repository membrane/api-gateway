/* Copyright 2015 predic8 GmbH, www.predic8.com

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
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;

import java.util.ArrayList;
import java.util.List;

/**
 * @description Supplies a private key and its certificate chain directly as PEM blocks, as an
 * alternative to loading them from a <code>keystore</code> file. See
 * <tt>tutorials/ssl-tls/10-TLS-Termination.yaml</tt>.
 * @yaml <pre><code>
 * ssl:
 *   key:
 *     private:
 *       location: membrane-key.pem
 *     certificates:
 *       - location: membrane.pem
 * </code></pre>
 */
@MCElement(name="key")
public class Key {
    @MCElement(name="private", mixed=true)
    public static class Private extends Blob {
        /**
         * @description A file/resource containing the private key in PEM format.
         * See <a href="https://www.membrane-soa.org/service-proxy-doc/current/configuration/location.htm">here</a> for a description of the format.
         */
        public void setLocation(String location) {
            super.setLocation(location);
        }

        /**
         * @description The key in PEM format, given inline as the element's text content instead
         * of via <code>location</code>.
         */
        public void setContent(String content) {
            super.setContent(content);
        }
    }

    String password;
    Private private_;
    List<Certificate> certificates = new ArrayList<>();

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Key))
            return false;
        Key other = (Key)obj;
        return Objects.equal(password, other.password)
                && Objects.equal(private_, other.private_)
                && Objects.equal(certificates, other.certificates);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(password, private_, certificates);
    }

    public String getPassword() {
        return password;
    }

    /**
     * @description Password protecting the private key, if the PEM block is encrypted.
     * @default <i>not set</i>
     */
    @MCAttribute
    public void setPassword(String password) {
        this.password = password;
    }

    public Private getPrivate() {
        return private_;
    }

    /**
     * @description The private key, as a PEM block either inline or loaded from a file.
     */
    @Required
    @MCChildElement(order=1)
    public void setPrivate(Private private_) {
        this.private_ = private_;
    }

    public List<Certificate> getCertificates() {
        return certificates;
    }

    /**
     * @description The certificate chain for the private key: the leaf certificate first,
     * followed by any intermediates, each as its own PEM block either inline or loaded from a
     * file.
     */
    @Required
    @MCChildElement(order=2)
    public void setCertificates(List<Certificate> certificates) {
        this.certificates = certificates;
    }
}
