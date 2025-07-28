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

import com.fasterxml.jackson.core.type.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.parameter.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import org.apache.commons.io.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.net.URI;
import java.util.*;

@MCElement(name="membrane")
public class MembraneAuthorizationService extends AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(MembraneAuthorizationService.class.getName());

    public static final String WELL_KNOWN_OPENID_CONFIGURATION_PATH = ".well-known/openid-configuration";

    /** Supported OIDC response?modes. */
    public static final String FORM_POST_RESPONSE_MODE = "form_post";
    public static final String QUERY_RESPONSE_MODE = "query";
    public static final String FRAGMENT_RESPONSE_MODE = "fragment";

    /** Fallback when the server lists no modes. */
    public static final List<@NotNull String> DEFAULT_RESPONSE_MODES = List.of(QUERY_RESPONSE_MODE, FRAGMENT_RESPONSE_MODE);

    private String src; // url to OpenID-Provider data
    private String internalSrc;

    private String issuer;
    private String tokenEndpoint;
    private String userInfoEndpoint;
    private String subject = ClaimRenamer.convert("sub");
    private String authorizationEndpoint;
    private String publicAuthorizationEndpoint;
    private String revocationEndpoint;
    private String registrationEndpoint;
    private String jwksEndpoint;
    private String endSessionEndpoint;
    private String claims;
    private String claimsIdt;
    private String claimsParameter;

    /**
     * Preferred modes in priority order.
     */
    private List<String> responseModesSupported = List.of(FORM_POST_RESPONSE_MODE, QUERY_RESPONSE_MODE, FRAGMENT_RESPONSE_MODE);

    private String responseMode;

    private DynamicRegistration dynamicRegistration;

    protected boolean encodedScope;

    @Override
    public void init() throws Exception {
        if(src == null)
            throw new Exception("No wellknown file source configured. - Cannot work without one");
        if(dynamicRegistration != null){
            dynamicRegistration.init(router);
            supportsDynamicRegistration = true;
        }
        parseSrc(resolve(
                router.getResolverMap(),
                router.getBaseLocation(),
                getWellKnownUrl(internalSrc == null ? src : internalSrc)));
        if(internalSrc != null) {
            publicAuthorizationEndpoint = src + new URI(authorizationEndpoint).getPath();
        }
        adjustScope();
        prepareClaimsForLoginUrl();
    }

    private @NotNull String getWellKnownUrl(String baseUrl) {
        return baseUrl + (baseUrl.endsWith("/") ? "" : "/") + WELL_KNOWN_OPENID_CONFIGURATION_PATH;
    }

    public InputStream resolve(ResolverMap rm, String baseLocation, String url) throws Exception {
        return dynamicRegistration != null ?
                dynamicRegistration.retrieveOpenIDConfiguration(url) :
                super.resolve(rm, baseLocation, url);
    }

    @Override
    public String getIssuer() {
        return issuer;
    }

    @Override
    public String getJwksEndpoint() throws Exception {
        return jwksEndpoint;
    }

    @Override
    public String getEndSessionEndpoint() throws Exception {
        return endSessionEndpoint;
    }

    @Override
    protected void doDynamicRegistration(List<String> callbackURLs) throws Exception {
        if(dynamicRegistration == null || registrationEndpoint == null || registrationEndpoint.isEmpty())
            throw new RuntimeException("A registration bean is required and src needs to specify a registration endpoint");

        dynamicRegistrationIfNeeded(callbackURLs);
    }

    private void dynamicRegistrationIfNeeded(List<String> callbackUris) throws Exception {
        Client client = dynamicRegistration.registerWithCallbackAt(callbackUris, registrationEndpoint);
        setClientIdAndSecret(client.getClientId(), client.getClientSecret());
    }

    private void prepareClaimsForLoginUrl() throws IOException {
        claimsParameter = ClaimsParameter.writeCompleteJson(claims,claimsIdt);
        if(claimsParameter.isEmpty())
            claimsParameter = null;
    }

    @Override
    public void setScope(String scope) {
        super.setScope(scope);
        encodedScope = false;
    }

    private void adjustScope() {
        if(scope == null)
            scope = "profile";
        if (!encodedScope) {
            scope = OAuth2Util.urlencode(scope);
            encodedScope = true;
        }
    }

    private void parseSrc(InputStream resolve) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(IOUtils.toString(resolve));

        // without checks
        tokenEndpoint = json.path("token_endpoint").asText(null);
        if (tokenEndpoint == null)
            throw new RuntimeException("No token_endpoint could be detected.");
        userInfoEndpoint = json.path("userinfo_endpoint").asText(null);
        authorizationEndpoint = json.path("authorization_endpoint").asText();
        revocationEndpoint = json.path("revocation_endpoint").asText();
        registrationEndpoint = json.path("registration_endpoint").asText(null);
        jwksEndpoint = json.path("jwks_uri").asText(null);
        endSessionEndpoint = json.path("end_session_endpoint").asText(null);
        issuer = json.path("issuer").asText(null);

        log.debug("Configured response modes: {}", responseModesSupported);
        List<String> responseModesOfferedFromServer = convertToListOfStrings(mapper, json.get("response_modes_supported"));
        log.debug("Server offered response modes: {}", responseModesOfferedFromServer);
        responseMode = negotiateResponseMode(responseModesOfferedFromServer);
        log.debug("Aggreed on response mode: {}", responseMode);
    }

    private static List<String> convertToListOfStrings(ObjectMapper mapper, JsonNode json) {
        return mapper.convertValue(json, new TypeReference<>() {});
    }

    String negotiateResponseMode(List<String> offered) {
        List<String> effective = (offered == null || offered.isEmpty()) ? DEFAULT_RESPONSE_MODES : offered;
        return responseModesSupported.stream()
                .filter(effective::contains)
                .findFirst()
                .orElseThrow(() ->
                        new ConfigurationException(
                                "No matching response mode. Supported=" + responseModesSupported +
                                ", offered=" + effective));
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    @Override
    public String getRevocationEndpoint() {
        return revocationEndpoint;
    }

    @Override
    public String getLoginURL(String callbackURL) {
        String endpoint = publicAuthorizationEndpoint;
        if(endpoint == null)
            endpoint = authorizationEndpoint;
        return endpoint +"?"+
                "client_id=" + getClientId() + "&"+
                "response_type=code&"+
                "scope="+scope+"&"+
                "redirect_uri=" + callbackURL +
                "&response_mode=" + responseMode +
                getClaimsParameter();
    }

    private String getClaimsParameter() {
        if(claimsParameter == null)
            return "";
        return "&claims=" + OAuth2Util.urlencode(claimsParameter);
    }

    @Override
    public String getUserInfoEndpoint() {
        return userInfoEndpoint;
    }

    @Override
    public String getSubject() {
        return subject;
    }

    @MCAttribute
    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSrc() {
        return src;
    }

    /**
     * Base URL of the OIDC Discovery compliant Authorization Server.
     */
    @Required
    @MCAttribute
    public void setSrc(String src) {
        this.src = src;
    }

    public String getInternalSrc() {
        return internalSrc;
    }

    /**
     * Internal Base URL of the OIDC Discovery compliant Authorization Server. Needs only to be set,
     * if access to the Authorization Server is also routed via Membrane API Gateway. Usually points
     * to an internal proxy, e.g. "internal://oauth2-gw/", which routes to the Authorization Server.
     */
    @MCAttribute
    public void setInternalSrc(String internalSrc) {
        this.internalSrc = internalSrc;
    }

    public String getClaims() {
        return claims;
    }

    /**
     *
     * @description claims that are requested for the userinfo endpoint
     */
    @MCAttribute
    public void setClaims(String claims) {
        this.claims = claims;
    }

    public String getClaimsIdt() {
        return claimsIdt;
    }

    /**
     *
     * @description claims that are requested for the id_token
     */
    @MCAttribute
    public void setClaimsIdt(String claimsIdt) {
        this.claimsIdt = claimsIdt;
    }

    public DynamicRegistration getDynamicRegistration() {
        return dynamicRegistration;
    }

    /**
     * @description defines a chain of interceptors that are run for the dynamic registration process of openid-connect
     */
    @MCChildElement(order=10)
    public void setDynamicRegistration(DynamicRegistration dynamicRegistration) {
        this.dynamicRegistration = dynamicRegistration;
    }

    public List<String> getResponseModesSupported() {
        return responseModesSupported;
    }

    /**
     * @description Comma? or blank?separated preference list of response modes (highest priority first).
     * Example: <membrane responseModesSupported="form_post query"/>
     *
     * @default form_post query fragment
     */
    @MCAttribute
    public void setResponseModesSupported(List<String> responseModesSupported) {
        this.responseModesSupported = responseModesSupported;
    }

    public String getResponseMode() {
        return responseMode;
    }
}