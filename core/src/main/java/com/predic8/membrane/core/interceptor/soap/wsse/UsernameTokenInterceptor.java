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
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.lang.TemplateExchangeExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static com.predic8.membrane.core.util.text.SerializationFunction.TEXT_SERIALIZATION;

/**
 * @description Adds a WS-Security <code>UsernameToken</code> to the SOAP header of the request.
 * Reuses an existing <code>soap:Header</code>/<code>wsse:Security</code> element if present,
 * otherwise creates them. Username and password are evaluated as SpEL template expressions,
 * so both static values and dynamic values (e.g. <code>${property.apiUser}</code>) are supported.
 * @topic 3. Security
 */
@MCElement(name = "usernameToken")
public class UsernameTokenInterceptor extends AbstractSoapDomInterceptor {

    public enum PasswordType {PLAIN_TEXT, DIGEST}

    private String username;
    private String password;
    private PasswordType passwordType = PasswordType.PLAIN_TEXT;

    private ExchangeExpression usernameExpression;
    private ExchangeExpression passwordExpression;

    @Override
    public void init() {
        super.init();
        usernameExpression = TemplateExchangeExpression.newInstance(this, SPEL, username, router, TEXT_SERIALIZATION);
        passwordExpression = TemplateExchangeExpression.newInstance(this, SPEL, password, router, TEXT_SERIALIZATION);
    }

    @Override
    protected String notSoapDetail() {
        return "no wsse:UsernameToken could be added.";
    }

    @Override
    protected String internalErrorDetail() {
        return "Could not add wsse:UsernameToken to SOAP body.";
    }

    @Override
    protected Outcome handleDocument(Exchange exc, Document doc) throws Exception {
        String user = usernameExpression.evaluate(exc, Flow.REQUEST, String.class);
        String pass = passwordExpression.evaluate(exc, Flow.REQUEST, String.class);

        Element envelope = doc.getDocumentElement();
        String soapNs = envelope.getNamespaceURI();

        Element security = getOrCreateSecurity(doc, getOrCreateHeader(doc, envelope, soapNs));
        security.appendChild(createUsernameToken(doc, user, pass));

        writeBack(exc, doc);
        return CONTINUE;
    }

    private Element createUsernameToken(Document doc, String user, String pass) throws Exception {
        Element usernameToken = doc.createElementNS(WSSE_NS, "wsse:UsernameToken");
        usernameToken.appendChild(textElement(doc, WSSE_NS, "wsse:Username", user));
        if (passwordType == PasswordType.DIGEST) {
            appendDigestPassword(doc, usernameToken, pass);
        } else {
            usernameToken.appendChild(passwordElement(doc, PASSWORD_TEXT_TYPE, pass));
        }
        return usernameToken;
    }

    private static void appendDigestPassword(Document doc, Element usernameToken, String pass) throws Exception {
        byte[] nonce = generateNonce();
        String created = Instant.now().toString();

        usernameToken.appendChild(passwordElement(doc, PASSWORD_DIGEST_TYPE, usernameTokenDigest(nonce, created, pass)));

        Element nonceEl = textElement(doc, WSSE_NS, "wsse:Nonce", Base64.getEncoder().encodeToString(nonce));
        nonceEl.setAttribute("EncodingType", BASE64_BINARY_ENCODING_TYPE);
        usernameToken.appendChild(nonceEl);

        usernameToken.appendChild(textElement(doc, WSU_NS, "wsu:Created", created));
    }

    private static Element passwordElement(Document doc, String type, String content) {
        Element passwordEl = textElement(doc, WSSE_NS, "wsse:Password", content);
        passwordEl.setAttribute("Type", type);
        return passwordEl;
    }

    private static Element textElement(Document doc, String namespace, String qualifiedName, String text) {
        Element element = doc.createElementNS(namespace, qualifiedName);
        element.setTextContent(text);
        return element;
    }

    private static byte[] generateNonce() {
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    public String getUsername() {
        return username;
    }

    /**
     * @description Username to place in the UsernameToken. Evaluated as a SpEL template
     * expression, so both static values and expressions (e.g. <code>${property.apiUser}</code>)
     * are supported.
     * @example ${property.apiUser}
     */
    @MCAttribute
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * @description Password to place in the UsernameToken. Evaluated as a SpEL template
     * expression, so both static values and expressions (e.g. <code>${property.apiPassword}</code>)
     * are supported.
     * @example ${property.apiPassword}
     */
    @MCAttribute
    public void setPassword(String password) {
        this.password = password;
    }

    public PasswordType getPasswordType() {
        return passwordType;
    }

    /**
     * @description Whether the password is sent as plain text or as a WS-Security digest.
     * @default PLAIN_TEXT
     * @example DIGEST
     */
    @MCAttribute
    public void setPasswordType(PasswordType passwordType) {
        this.passwordType = passwordType;
    }
}
