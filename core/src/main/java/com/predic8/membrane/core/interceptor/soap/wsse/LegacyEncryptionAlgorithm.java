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

/** Supported, explicit exceptions to the inbound encryption algorithm defaults. */
public enum LegacyEncryptionAlgorithm {
    AES128_CBC(XmlEncryptionUtil.AES128_CBC),
    AES192_CBC(XmlEncryptionUtil.AES192_CBC),
    AES256_CBC(XmlEncryptionUtil.AES256_CBC),
    RSA_1_5(XmlEncryptionUtil.RSA_1_5);

    private final String uri;

    LegacyEncryptionAlgorithm(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }
}
