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

import com.predic8.membrane.annot.MCElement;

/**
 * @description References the signing certificate from <code>ds:KeyInfo</code> via a
 * <code>wsse:SecurityTokenReference</code>/<code>wsse:Reference</code> pointing at a
 * <code>wsse:BinarySecurityToken</code> that is inserted into <code>wsse:Security</code>.
 * This is the WS-Security "Direct Reference" convention used by Apache CXF, as an alternative
 * to embedding the certificate inline via <code>x509Data</code>.
 */
@MCElement(name = "securityTokenReference", component = false, id = "digitalSignature-securityTokenReference")
public class SecurityTokenReferenceKeyInfo {
}
