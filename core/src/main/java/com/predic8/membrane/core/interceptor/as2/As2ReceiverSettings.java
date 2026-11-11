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

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * Settings of an as2Receiver, checked and loaded by the interceptor.
 *
 * @param as2Id            Own AS2 ID; the AS2-To header of incoming messages has to match it
 * @param privateKey       Own private key (RSA or EC): decrypts messages and signs MDNs
 * @param certificate      Certificate of the private key
 * @param partners         Certificate each partner signs its messages with, by AS2 ID of the partner
 * @param requireSigned    Reject unsigned messages
 * @param requireEncrypted Reject unencrypted messages
 * @param maxPayloadSize   Maximum size of the payload in bytes, also the limit for decompression
 */
public record As2ReceiverSettings(String as2Id, PrivateKey privateKey, X509Certificate certificate,
                                  Map<String, X509Certificate> partners, boolean requireSigned,
                                  boolean requireEncrypted, long maxPayloadSize) {

    public As2ReceiverSettings {
        partners = Map.copyOf(partners);
    }

    /**
     * Without the private key, which some key implementations print with its secret values.
     */
    @Override
    public String toString() {
        return "As2ReceiverSettings[as2Id=%s, certificate=%s, partners=%s, requireSigned=%s, requireEncrypted=%s, maxPayloadSize=%d]"
                .formatted(as2Id, certificate.getSubjectX500Principal(), partners.keySet(), requireSigned, requireEncrypted, maxPayloadSize);
    }
}
