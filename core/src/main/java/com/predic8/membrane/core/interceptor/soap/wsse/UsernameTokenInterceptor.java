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
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.lang.TemplateExchangeExpression;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.util.SOAPUtil;
import com.predic8.membrane.core.util.xml.XMLUtil;
import com.predic8.membrane.core.util.xml.parser.HardenedXmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSU_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.getOrCreateHeader;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.getOrCreateSecurity;
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
public class UsernameTokenInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UsernameTokenInterceptor.class);

    private static final String USERNAME_TOKEN_PROFILE_NS =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0";
    private static final String PASSWORD_TEXT_TYPE = USERNAME_TOKEN_PROFILE_NS + "#PasswordText";
    private static final String PASSWORD_DIGEST_TYPE = USERNAME_TOKEN_PROFILE_NS + "#PasswordDigest";
    // Note: distinct from WSSE_NS (the wsse secext schema itself) - this is the WS-Security SOAP
    // Message Security namespace that actually defines the Base64Binary encoding type.
    private static final String BASE64_BINARY_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary";

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
    public Outcome handleRequest(Exchange exc) {
        if (!SOAPUtil.analyseSOAPMessage(new XOPReconstitutor(), exc.getRequest()).isSOAP()) {
            user(router.getConfiguration().isProduction(), getDisplayName())
                    .title("Not a SOAP message.")
                    .detail("Request body is not XML or does not contain a SOAP body, so no wsse:UsernameToken could be added.")
                    .buildAndSetResponse(exc);
            return ABORT;
        }

        try {
            String user = usernameExpression.evaluate(exc, Flow.REQUEST, String.class);
            String pass = passwordExpression.evaluate(exc, Flow.REQUEST, String.class);

            Document doc = HardenedXmlParser.getInstance().parse(XMLUtil.getInputSource(exc.getRequest()));

            Element envelope = doc.getDocumentElement();
            String soapNs = envelope.getNamespaceURI();

            Element header = getOrCreateHeader(doc, envelope, soapNs);
            Element security = getOrCreateSecurity(doc, header);
            security.appendChild(createUsernameToken(doc, user, pass));

            exc.getRequest().setBodyContent(XMLUtil.xmlNode2String(doc).getBytes(StandardCharsets.UTF_8));
            return CONTINUE;
        } catch (Exception e) {
            log.warn("Could not add wsse:UsernameToken to SOAP body", e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .detail("Could not add wsse:UsernameToken to SOAP body.")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private Element createUsernameToken(Document doc, String user, String pass) throws Exception {
        Element usernameToken = doc.createElementNS(WSSE_NS, "wsse:UsernameToken");

        Element usernameEl = doc.createElementNS(WSSE_NS, "wsse:Username");
        usernameEl.setTextContent(user);
        usernameToken.appendChild(usernameEl);

        Element passwordEl = doc.createElementNS(WSSE_NS, "wsse:Password");
        if (passwordType == PasswordType.DIGEST) {
            String nonce = generateNonce();
            String created = Instant.now().toString();

            passwordEl.setAttribute("Type", PASSWORD_DIGEST_TYPE);
            passwordEl.setTextContent(computeDigest(nonce, created, pass));
            usernameToken.appendChild(passwordEl);

            Element nonceEl = doc.createElementNS(WSSE_NS, "wsse:Nonce");
            nonceEl.setAttribute("EncodingType", BASE64_BINARY_TYPE);
            nonceEl.setTextContent(nonce);
            usernameToken.appendChild(nonceEl);

            Element createdEl = doc.createElementNS(WSU_NS, "wsu:Created");
            createdEl.setTextContent(created);
            usernameToken.appendChild(createdEl);
        } else {
            passwordEl.setAttribute("Type", PASSWORD_TEXT_TYPE);
            passwordEl.setTextContent(pass);
            usernameToken.appendChild(passwordEl);
        }

        return usernameToken;
    }

    private static String generateNonce() {
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }

    private static String computeDigest(String nonce, String created, String password) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(Base64.getDecoder().decode(nonce));
        sha1.update(created.getBytes(StandardCharsets.UTF_8));
        sha1.update(password.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sha1.digest());
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
