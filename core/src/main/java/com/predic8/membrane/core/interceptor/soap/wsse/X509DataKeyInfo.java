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
 * @description Embeds the signing certificate directly in <code>ds:KeyInfo</code> as a
 * <code>ds:X509Data</code>/<code>ds:X509Certificate</code>. This is the default
 * <code>digitalSignature</code> behavior when no key-info element
 * (<code>x509Data</code>, <code>securityTokenReference</code>, or <code>keyIdentifier</code>)
 * is configured.
 */
@MCElement(name = "x509Data", component = false, id = "digitalSignature-x509Data")
public class X509DataKeyInfo {
}
