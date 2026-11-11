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

import com.predic8.membrane.core.interceptor.as2.As2SenderInterceptor.Compression;
import com.predic8.membrane.core.interceptor.as2.As2SenderInterceptor.Encryption;
import com.predic8.membrane.core.interceptor.as2.As2SenderInterceptor.Mdn;
import com.predic8.membrane.core.interceptor.as2.As2SenderInterceptor.SignatureDigest;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * Settings of an as2Sender, checked and loaded by the interceptor.
 *
 * @param as2Id                     Own AS2 ID, sent as AS2-From
 * @param privateKey                Own private key (RSA or EC): signs messages
 * @param certificate               Certificate of the private key
 * @param partnerAs2Id              AS2 ID of the partner, sent as AS2-To
 * @param partnerCertificate        Certificate of the partner: messages are encrypted for it, and MDNs have to be
 *                                  signed with it
 * @param signatureDigest           Digest algorithm of the signature and the MIC
 * @param encryption                Encryption of messages, RSA certificate of the partner if not NONE
 * @param compression               Compression of messages
 * @param mdn                       Which MDN is requested from the partner
 * @param subject                   Subject header of messages
 * @param dispositionNotificationTo Disposition-Notification-To header, sent if an MDN is requested
 * @param messageIdDomain           Domain part of generated Message-IDs
 * @param maxPayloadSize            Maximum size of the payload in bytes
 */
public record As2SenderSettings(String as2Id, PrivateKey privateKey, X509Certificate certificate,
                                String partnerAs2Id, X509Certificate partnerCertificate,
                                SignatureDigest signatureDigest, Encryption encryption, Compression compression,
                                Mdn mdn, String subject, String dispositionNotificationTo, String messageIdDomain,
                                long maxPayloadSize) {

    /**
     * Without the private key, which some key implementations print with its secret values.
     */
    @Override
    public String toString() {
        return "As2SenderSettings[as2Id=%s, certificate=%s, partner=%s, partnerCertificate=%s, signatureDigest=%s, encryption=%s, compression=%s, mdn=%s, maxPayloadSize=%d]"
                .formatted(as2Id, certificate.getSubjectX500Principal(), partnerAs2Id,
                        partnerCertificate.getSubjectX500Principal(), signatureDigest, encryption, compression, mdn,
                        maxPayloadSize);
    }
}
