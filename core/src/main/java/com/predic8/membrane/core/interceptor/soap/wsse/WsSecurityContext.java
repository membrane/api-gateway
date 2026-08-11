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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Everything a {@link WsSecurityPart} may read about the message currently being processed. One
 * instance is shared by every part of a group, so all parts see - and mutate - the same
 * {@link Document}; the enclosing {@link WsSecurityInterceptor} publishes it to the message once,
 * after the last part has run.
 *
 * @param flow     which of the exchange's messages {@link #document()} was parsed from; parts that
 *                 evaluate expressions must pass this on rather than assuming the request
 * @param security the {@code wsse:Security} element of this group - the one being consumed in a
 *                 {@code validate} group, the freshly created one in a {@code secure} group
 */
record WsSecurityContext(Exchange exchange, Interceptor.Flow flow, Document document,
                         Element envelope, String soapNs, Element security) {
}
