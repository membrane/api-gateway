/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.as2;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.Certificate;

/**
 * @description A trading partner, identified by its AS2 ID and its certificate. An <code>as2Receiver</code> accepts
 * messages from it; an <code>as2Sender</code> sends messages to it.
 */
@MCElement(name = "partner", component = false)
public class As2Partner {

    private String as2Id;
    private Certificate certificate;

    /**
     * @description AS2 ID of the partner. An <code>as2Receiver</code> assigns incoming messages to the partner by
     * their <code>AS2-From</code> header; an <code>as2Sender</code> sends it as <code>AS2-To</code>. 1 to 128 printable
     * ASCII characters, compared case-sensitively.
     * @example ACME
     */
    @MCAttribute
    public void setAs2Id(String as2Id) {
        this.as2Id = as2Id;
    }

    public String getAs2Id() {
        return as2Id;
    }

    /**
     * @description Certificate of the partner in PEM format. The partner signs its messages and MDNs with it, and an
     * <code>as2Sender</code> encrypts messages for it. Signatures are verified only against this certificate;
     * certificates embedded in a message are never trusted.
     */
    @MCChildElement
    public void setCertificate(Certificate certificate) {
        this.certificate = certificate;
    }

    public Certificate getCertificate() {
        return certificate;
    }
}
