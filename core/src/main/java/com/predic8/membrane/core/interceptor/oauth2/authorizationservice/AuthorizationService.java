/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2.authorizationservice;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.JwtGenerator;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.JWSSigner;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.ssl.PEMSupport;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import org.apache.commons.codec.binary.Base64;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

import static com.predic8.membrane.core.Constants.USERAGENT;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;

public abstract class AuthorizationService {
    protected Logger log;

    private HttpClient httpClient;
    protected Router router;

    protected HttpClientConfiguration httpClientConfiguration;
    private final Object lock = new Object();
    @GuardedBy("lock")
    private String clientId;
    @GuardedBy("lock")
    private String clientSecret;
    private JWSSigner JWSSigner;
    protected String scope;
    private SSLParser sslParser;
    private SSLContext sslContext;
    private boolean useJWTForClientAuth;

    protected boolean supportsDynamicRegistration = false;

    public boolean supportsDynamicRegistration() {
        return supportsDynamicRegistration;
    }


    public void init(Router router) throws Exception {
        log = LoggerFactory.getLogger(this.getClass().getName());

        if (isUseJWTForClientAuth()) {
            JWSSigner = new JWSSigner(PEMSupport.getInstance().parseKey(getSslParser().getKey().getPrivate().get(router.getResolverMap(), router.getBaseLocation())),
                    getSslParser().getKey().getCertificates().getFirst().get(router.getResolverMap(), router.getBaseLocation()));
        }

        setHttpClient(router.getHttpClientFactory().createClient(getHttpClientConfiguration()));
        if (sslParser != null)
            sslContext = new StaticSSLContext(sslParser, router.getResolverMap(), router.getBaseLocation());
        this.router = router;
        init();
        if (!supportsDynamicRegistration())
            checkForClientIdAndSecret();
    }

    public abstract void init() throws Exception;

    public abstract String getIssuer();

    public abstract String getJwksEndpoint() throws Exception;

    public abstract String getLoginURL(String securityToken, String callbackURL, String callbackPath);

    public abstract String getUserInfoEndpoint();

    public abstract String getSubject();

    public abstract String getTokenEndpoint();

    public abstract String getRevocationEndpoint();

    protected void doDynamicRegistration(List<String> callbackURLs) throws Exception {
    }

    public void dynamicRegistration(List<String> callbackURLs) throws Exception {
        if (supportsDynamicRegistration())
            doDynamicRegistration(callbackURLs);
    }

    protected void checkForClientIdAndSecret() {
        synchronized (lock) {
            if (clientId == null)
                throw new RuntimeException(this.getClass().getSimpleName() + " cannot work without specified clientId");
            if (clientSecret == null && sslParser == null)
                throw new RuntimeException(this.getClass().getSimpleName() + " cannot work without either clientSecret or a client key+certificate");
        }
    }


    public HttpClientConfiguration getHttpClientConfiguration() {
        return httpClientConfiguration;
    }

    @MCAttribute
    public void setHttpClientConfiguration(HttpClientConfiguration httpClientConfiguration) {
        this.httpClientConfiguration = httpClientConfiguration;
    }

    public String getClientId() {
        synchronized (lock) {
            return clientId;
        }
    }

    @MCAttribute
    public void setClientId(String clientId) {
        synchronized (lock) {
            this.clientId = clientId;
        }
    }

    public String getClientSecret() {
        synchronized (lock) {
            return clientSecret;
        }
    }

    @MCAttribute
    public void setClientSecret(String clientSecret) {
        synchronized (lock) {
            this.clientSecret = clientSecret;
        }
    }

    protected void setClientIdAndSecret(String clientId, String clientSecret) {
        synchronized (lock) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }
    }

    public String getScope() {
        return scope;
    }

    @MCAttribute
    public void setScope(String scope) {
        this.scope = scope;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Response doRequest(Exchange e) throws Exception {
        if (sslContext != null)
            e.setProperty(Exchange.SSL_CONTEXT, sslContext);
        return getHttpClient().call(e).getResponse();
    }

    public SSLParser getSslParser() {
        return sslParser;
    }

    @MCChildElement(order = 20, allowForeign = true)
    public void setSslParser(SSLParser sslParser) {
        this.sslParser = sslParser;
    }

    public boolean isUseJWTForClientAuth() {
        return useJWTForClientAuth;
    }

    @MCAttribute
    public void setUseJWTForClientAuth(boolean useJWTForClientAuth) {
        this.useJWTForClientAuth = useJWTForClientAuth;
    }

    public JWSSigner getJwtKeyCertHandler() {
        return JWSSigner;
    }

    public Request.Builder applyAuth(Request.Builder requestBuilder, String body) {

        if (isUseJWTForClientAuth()) {
            body += "&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer" + "&client_assertion=" + createClientToken();
        }

        String clientSecret = getClientSecret();
        if (clientSecret != null)
            requestBuilder.header(AUTHORIZATION, "Basic " + new String(Base64.encodeBase64((getClientId() + ":" + clientSecret).getBytes()))).body(body);
        else requestBuilder.body(body + "&client_id" + getClientId());
        return requestBuilder;
    }

    public Response refreshTokenRequest(OAuth2AnswerParameters params) throws Exception {
        return doRequest(applyAuth(
                new Request.Builder().post(getTokenEndpoint())
                        .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                        .header(ACCEPT, APPLICATION_JSON)
                        .header(USER_AGENT, USERAGENT),
                "grant_type=refresh_token" + "&refresh_token=" + params.getRefreshToken())
                .buildExchange());
    }

    public Response requestUserEndpoint(OAuth2AnswerParameters params) throws Exception {
        return doRequest(new Request.Builder()
                .get(getUserInfoEndpoint())
                .header("Authorization", params.getTokenType() + " " + params.getAccessToken())
                .header("User-Agent", USERAGENT)
                .header(ACCEPT, APPLICATION_JSON)
                .buildExchange());
    }

    public boolean idTokenIsValid(String idToken) {
        //TODO maybe change this to return claims and also save them in the oauth2AnswerParameters
        try {
            JwtGenerator.getClaimsFromSignedIdToken(idToken, getIssuer(), getClientId(), getJwksEndpoint(), this);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String createClientToken() {
        try {
            String jwtSub = this.getClientId();
            String jwtAud = this.getTokenEndpoint();

            // see https://docs.microsoft.com/en-us/azure/active-directory/develop/active-directory-certificate-credentials
            JwtClaims jwtClaims = new JwtClaims();
            jwtClaims.setSubject(jwtSub);
            jwtClaims.setAudience(jwtAud);
            jwtClaims.setIssuer(jwtClaims.getSubject());
            jwtClaims.setJwtId(UUID.randomUUID().toString());
            jwtClaims.setIssuedAtToNow();
            NumericDate expiration = NumericDate.now();
            expiration.addSeconds(300);
            jwtClaims.setExpirationTime(expiration);
            jwtClaims.setNotBeforeMinutesInThePast(2f);

            JsonWebSignature jws = new JsonWebSignature();
            jws.setPayload(jwtClaims.toJson());
            jws.setKey(jwtKeyCertHandler.getKey());
            jws.setX509CertSha1ThumbprintHeaderValue(jwtKeyCertHandler.getCertificate());
            jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
            jws.setHeader("typ", "JWT");

            return jws.getCompactSerialization();
        } catch (JoseException | MalformedClaimException e) {
            throw new RuntimeException(e);
        }
    }

}
