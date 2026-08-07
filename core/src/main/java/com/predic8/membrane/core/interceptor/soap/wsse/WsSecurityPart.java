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
 * One step of a {@link WsSecurityInterceptor}'s {@code validate} or {@code secure} list.
 * <p>
 * The SPI is deliberately defined over the shared {@link org.w3c.dom.Document} of
 * {@link WsSecurityContext} and over nothing else (see ADR-006): JSR-105 types such as
 * {@code XMLSignatureFactory} must not appear here, so that an implementation may be replaced -
 * hand-rolled or library-backed - without touching the configuration grammar.
 * <p>
 * Parts are stateless with respect to a single message: everything derived from configuration is
 * resolved in {@link #init()}, everything derived from the message lives in the
 * {@link WsSecurityContext} passed to {@link #process(WsSecurityContext)}.
 */
public abstract class WsSecurityPart {

    /**
     * The enclosing element, which owns the keystore, truststore, namespace declarations and the
     * actor this part's {@code wsse:Security} header is targeted at.
     */
    protected WsSecurityInterceptor parent;

    final void init(WsSecurityInterceptor parent) {
        this.parent = parent;
        init();
    }

    /**
     * Validates this part's configuration and resolves whatever it needs from
     * {@link #parent} - keys, certificates, compiled expressions.
     *
     * @throws com.predic8.membrane.core.util.ConfigurationException if the configuration is invalid
     */
    protected void init() {
    }

    /**
     * Consumes or applies this part's security on the shared document.
     *
     * @throws WsSecurityFaultException if the message is not acceptable, naming the
     *                                  {@code soap:Fault} to return
     */
    abstract void process(WsSecurityContext ctx) throws Exception;
}
