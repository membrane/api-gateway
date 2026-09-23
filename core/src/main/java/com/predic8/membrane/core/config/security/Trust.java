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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @description Supplies the CA certificates to trust when validating a peer's certificate chain,
 * given directly as PEM blocks instead of loading them from a <code>truststore</code> file. Each
 * certificate can be a file/resource or inline PEM text.
 * <pre>
 * trust:
 *   certificates:
 *     - location: &lt;file&gt; | content: &lt;PEM text&gt;
 *     ...
 *   [ algorithm: &lt;name&gt; ]
 *   [ checkRevocation: &lt;options&gt; ]
 * </pre>
 * @yaml <pre><code>
 * ssl:
 *   trust:
 *     certificates:
 *       - location: ca.pem
 * </code></pre>
 */
@MCElement(name="trust")
public class Trust {
    protected String algorithm;
    protected String checkRevocation;
    List<Certificate> certificateList = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trust trust = (Trust) o;
        return Objects.equals(algorithm, trust.algorithm)
                && Objects.equals(checkRevocation, trust.checkRevocation)
                && Objects.equals(certificateList, trust.certificateList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(algorithm, checkRevocation, certificateList);
    }

    public List<Certificate> getCertificates() {
        return certificateList;
    }

    /**
     * @description The CA certificates to trust, each as its own PEM block either inline or
     * loaded from a file.
     */
    @MCChildElement
    public void setCertificates(List<Certificate> certificateList) {
        this.certificateList = certificateList;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * @description Trust manager algorithm used to validate certificate chains.
     * @default the JVM's default (usually <code>PKIX</code>)
     */
    @MCAttribute
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getCheckRevocation() {
        return checkRevocation;
    }

    /**
     * @description Comma-separated PKIX revocation checking options, from
     * <code>java.security.cert.PKIXRevocationChecker.Option</code>: <code>ONLY_END_ENTITY</code>,
     * <code>PREFER_CRLS</code>, <code>NO_FALLBACK</code>, <code>SOFT_FAIL</code>. Unset, no
     * revocation checking (CRL/OCSP) is performed at all.
     * @example ONLY_END_ENTITY,SOFT_FAIL
     */
    @MCAttribute
    public void setCheckRevocation(String checkRevocation) {
        this.checkRevocation = checkRevocation;
    }
}
