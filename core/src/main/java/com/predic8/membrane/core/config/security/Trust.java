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
 * @description Experimental.
 * <p>Allows to insert one or more PEM blocks containing the certificates to be trusted directly into the proxies.xml
 * file.</p>
 * <p>This is an alternative for {@link TrustStore}.</p>
 */
@MCElement(name="trust")
public class Trust {
    protected String algorithm;
    protected String checkRevocation;
    List<Certificate> certificateList = new ArrayList<Certificate>();

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

    public List<Certificate> getCertificateList() {
        return certificateList;
    }

    @MCChildElement
    public void setCertificateList(List<Certificate> certificateList) {
        this.certificateList = certificateList;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    @MCAttribute
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getCheckRevocation() {
        return checkRevocation;
    }

    @MCAttribute
    public void setCheckRevocation(String checkRevocation) {
        this.checkRevocation = checkRevocation;
    }
}
