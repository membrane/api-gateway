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

/**
 * A part of a {@code wsSecurity} element's {@code validate} list: it consumes inbound security,
 * checking what the peer sent and rejecting the message if it does not hold up.
 * <p>
 * A separate type from {@link SecurePart} because the two lists accept different element sets even
 * where the names coincide - a {@code signature} under {@code validate} verifies against a
 * truststore, one under {@code secure} signs with a keystore.
 */
public abstract class ValidatePart extends WsSecurityPart {
}
