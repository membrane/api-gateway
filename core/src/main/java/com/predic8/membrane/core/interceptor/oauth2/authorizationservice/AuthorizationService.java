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
import com.predic8.membrane.core.interceptor.oauth2.OAuth2TokenBody;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.JwtGenerator;
import com.predic8.membrane.core.interceptor.oauth2client.rf.LogHelper;
import com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2Exception;
import com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2TokenResponseBody;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.JWSSigner;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.ssl.PEMSupport;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import jakarta.mail.internet.ParseException;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static com.predic8.membrane.core.Constants.USERAGENT;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.predic8.membrane.core.http.Response.badRequest;
import static com.predic8.membrane.core.http.Response.internalServerError;
import static com.predic8.membrane.core.interceptor.oauth2.OAuth2TokenBody.authorizationCodeBodyBuilder;
import static com.predic8.membrane.core.interceptor.oauth2.OAuth2TokenBody.refreshTokenBodyBuilder;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.isJson;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2CallbackRequestHandler.MEMBRANE_MISSING_SESSION_DESCRIPTION;
import static org.apache.commons.codec.binary.Base64.encodeBase64;

public abstract class AuthorizationService {

    public static final String MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR_DESCRIPTION = "Error contacting the OAuth2 Authorization Server.";
    public static final String MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR = "MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR";

    protected Logger log;

    private HttpClient httpClient;
    Router router;

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
    private final LogHelper logHelper = new LogHelper();
    private ClientAuthorization clientAuthorization = ClientAuthorization.CLIENT_SECRET_BASIC;

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

    public abstract String getEndSessionEndpoint() throws Exception;

    public abstract String getLoginURL(String callbackURL);

    public abstract String getUserInfoEndpoint();

    public abstract String getSubject();

    /**
     * Note that this method does not honor the B2C flows. Use {@link #getTokenEndpoint(FlowContext)} instead.
     * @return The Token Endpoint URL.
     */
    protected abstract String getTokenEndpoint();

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
        logHelper.handleRequest(e);
        if (sslContext != null)
            e.setProperty(Exchange.SSL_CONTEXT, sslContext);
        Response response = getHttpClient().call(e).getResponse();
        logHelper.handleResponse(e);
        return response;
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

    public ClientAuthorization getClientAuthorization() {
        return clientAuthorization;
    }

    /**
     * @description Client Authorization method (see <a
     * href="https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication">OIDC
     * Core 1.0 chapter 9</a>
     * @default client_secret_basic
     */
    @MCAttribute
    public void setClientAuthorization(ClientAuthorization clientAuthorization) {
        this.clientAuthorization = clientAuthorization;
    }

    public JWSSigner getJwtKeyCertHandler() {
        return JWSSigner;
    }

    public Request.Builder applyAuth(Request.Builder requestBuilder, OAuth2TokenBody body, FlowContext flowContext) {

        if (isUseJWTForClientAuth()) {
            body.clientAssertion("urn:ietf:params:oauth:client-assertion-type:jwt-bearer", createClientToken(flowContext));
        }

        String clientSecret = getClientSecret();
        if (clientSecret == null) {
            return requestBuilder.body(body.clientId(getClientId()).build());
        }
        if (clientAuthorization == ClientAuthorization.CLIENT_SECRET_BASIC) {
            return requestBuilder.header(AUTHORIZATION, "Basic " + new String(encodeBase64((getClientId() + ":" + clientSecret).getBytes()))).body(body.build());
        }
        return requestBuilder.body(body.clientId(getClientId()).clientSecret(clientSecret).build());
    }


    public String getTokenEndpoint(@Nullable FlowContext flowContext) {
        String tokenEndpoint = getTokenEndpoint();
        if (flowContext != null) {
            tokenEndpoint = tokenEndpoint.replaceAll(flowContext.defaultFlow, flowContext.triggerFlow);
        }
        return tokenEndpoint;
    }

    public Response requestUserEndpoint(String tokenType, String token) throws Exception {
        return doRequest(new Request.Builder()
                .get(getUserInfoEndpoint())
                .header("Authorization", tokenType + " " + token)
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

    private String createClientToken(FlowContext flowContext) {
        try {
            String jwtSub = this.getClientId();
            String jwtAud = this.getTokenEndpoint(flowContext);

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

            String payload = jwtClaims.toJson();
            return JWSSigner.generateSignedJWS(payload);
        } catch (JoseException | MalformedClaimException e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream resolve(ResolverMap rm, String baseLocation, String url) throws Exception {
        url = ResolverMap.combine(baseLocation, url);
        // ask the internal httpClient (might be proxied/authenticated), if HTTP
        if (url.startsWith("http"))
            return httpClient.call(Request.get(url).buildExchange()).getResponse().getBodyAsStreamDecoded();
        return rm.resolve(url);
    }

    public OAuth2TokenResponseBody refreshTokenRequest(Session session, String wantedScope, String refreshToken) throws Exception {
        FlowContext fc = FlowContext.fromSession(session);
        Response response;
        try {
            response = doRequest(applyAuth(
                    new Request.Builder().post(getTokenEndpoint(fc))
                            .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                            .header(ACCEPT, APPLICATION_JSON)
                            .header(USER_AGENT, USERAGENT),
                    refreshTokenBodyBuilder(refreshToken).scope(wantedScope), fc)
                    .buildExchange());
        } catch (Exception e) {
            log.warn("Error contacting OAuth2 Authorization Server during refresh request: {}", e.getMessage());
            throw new OAuth2Exception(
                    MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR,
                    MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR_DESCRIPTION,
                    internalServerError().body(MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR_DESCRIPTION).build());
        }
        return parseTokenResponse(checkTokenResponse(response));
    }

    public OAuth2TokenResponseBody codeTokenRequest(String redirectUri, String code, String verifier, FlowContext flowContext) throws Exception {
        Response response;
        try {
            response = doRequest(applyAuth(
                    new Request.Builder()
                            .post(getTokenEndpoint(flowContext))
                            .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                            .header(ACCEPT, APPLICATION_JSON)
                            .header(USER_AGENT, USERAGENT),
                    authorizationCodeBodyBuilder(code, verifier).redirectUri(redirectUri), flowContext).buildExchange());
        } catch (Exception e) {
            log.warn("Error contacting OAuth2 Authorization Server during code request: {}", e.getMessage());
            throw new OAuth2Exception(
                    MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR,
                    MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR_DESCRIPTION,
                    internalServerError().body(MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR_DESCRIPTION).build());
        }
        return parseTokenResponse(checkTokenResponse(response));
    }

    private OAuth2TokenResponseBody parseTokenResponse(Response response) throws IOException {
        return OAuth2TokenResponseBody.parse(this, response.getBodyAsStreamDecoded());
    }

    private Response checkTokenResponse(Response response) throws IOException, ParseException {
        if (response.getStatusCode() != 200) {
            log.info("Authorization server response: {}", response.getBodyAsStringDecoded());
            throw new RuntimeException("Authorization server returned " + response.getStatusCode() + ".");
        }

        if (!isJson(response)) {
            response.getBody().read();
            throw new RuntimeException("Token response is no JSON.");
        }
        return response;
    }

}
