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
package com.predic8.membrane.core.interceptor.soap.wsse;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

/**
 * @description References the signing certificate from <code>ds:KeyInfo</code> via a
 * <code>wsse:SecurityTokenReference</code>/<code>wsse:KeyIdentifier</code>, instead of embedding
 * the certificate inline (<code>x509Data</code>) or via a separate <code>wsse:BinarySecurityToken</code>
 * (<code>securityTokenReference</code>). With <code>valueType=X509_V3</code> the certificate itself
 * is embedded in the <code>wsse:KeyIdentifier</code>; with <code>THUMBPRINT_SHA1</code> only its
 * SHA-1 thumbprint is, and the verifier looks the matching certificate up in its truststore.
 */
@MCElement(name = "keyIdentifier", component = false, id = "wsSecurity-signature-keyIdentifier")
public class KeyIdentifierKeyInfo {

    public enum ValueType {X509_V3, THUMBPRINT_SHA1}

    private ValueType valueType = ValueType.X509_V3;

    public ValueType getValueType() {
        return valueType;
    }

    /**
     * @description Whether the <code>wsse:KeyIdentifier</code> carries the full certificate
     * (<code>X509_V3</code>) or only its SHA-1 thumbprint (<code>THUMBPRINT_SHA1</code>), in which
     * case the verifier resolves the certificate from its truststore instead of the message.
     * @default X509_V3
     */
    @MCAttribute
    public void setValueType(ValueType valueType) {
        this.valueType = valueType;
    }
}
