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


import java.util.ArrayList;
import java.util.List;

/**
 * @description Experimental.
 * <p>Allows to insert one or more PEM blocks containing the certificates to be trusted directly into the proxies.xml
 * file.</p>
 * <p>This is an alternative for {@link TrustStore}.</p>
 */
public class Trust {
    List<Certificate> certificateList = new ArrayList<Certificate>();

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Trust))
            return false;
        Trust other = (Trust)obj;
        return Objects.equal(certificateList, other.certificateList);
    }

    public List<Certificate> getCertificateList() {
        return certificateList;
    }

    
    public void setCertificateList(List<Certificate> certificateList) {
        this.certificateList = certificateList;
    }
}