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
 * @description Names a certificate from <code>ds:KeyInfo</code> via a
 * <code>wsse:SecurityTokenReference</code>/<code>wsse:KeyIdentifier</code>, instead of embedding it
 * inline (<code>x509Data</code>). Under <code>signature</code> that is the signing certificate the
 * receiver verifies with; under <code>encrypt</code> it is the recipient's certificate, telling the
 * receiver which of its own keys decrypts the message. With <code>valueType=X509_V3</code> the
 * certificate itself is embedded in the <code>wsse:KeyIdentifier</code>; with
 * <code>THUMBPRINT_SHA1</code> only its SHA-1 thumbprint is, and the receiver looks the matching
 * certificate up in its own store.
 */
@MCElement(name = "keyIdentifier", component = false, id = "wsSecurity-signature-keyIdentifier")
public class KeyIdentifierKeyInfo {

    public enum ValueType {X509_V3, THUMBPRINT_SHA1}

    // Deliberately null rather than a constant: the useful default differs per parent - a signature
    // ships the certificate so the receiver can verify with it, while an encrypt names one the
    // receiver already holds - so the parent supplies it via valueTypeOrDefault().
    private ValueType valueType;

    public ValueType getValueType() {
        return valueType;
    }

    /**
     * The configured value type, or {@code fallback} when the attribute was omitted.
     */
    ValueType valueTypeOrDefault(ValueType fallback) {
        return valueType != null ? valueType : fallback;
    }

    /**
     * @description Whether the <code>wsse:KeyIdentifier</code> carries the full certificate
     * (<code>X509_V3</code>) or only its SHA-1 thumbprint (<code>THUMBPRINT_SHA1</code>), in which
     * case the receiver resolves the certificate from its own store instead of the message. When
     * omitted, <code>signature</code> uses <code>X509_V3</code> and <code>encrypt</code> uses
     * <code>THUMBPRINT_SHA1</code>.
     */
    @MCAttribute
    public void setValueType(ValueType valueType) {
        this.valueType = valueType;
    }
}
