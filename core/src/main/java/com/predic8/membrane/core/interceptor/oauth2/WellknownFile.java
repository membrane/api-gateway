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

package com.predic8.membrane.core.interceptor.oauth2;

import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.core.resolver.ResolverMap;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

public class WellknownFile {

    // taken from https://openid.net/specs/openid-connect-discovery-1_0.html
    private static final String ISSUER = "issuer";
    private static final String AUTHORIZATION_ENDPOINT = "authorization_endpoint";
    private static final String TOKEN_ENDPOINT = "token_endpoint";
    private static final String USERINFO_ENDPOINT = "userinfo_endpoint";
    private static final String REVOCATION_ENDPOINT = "revocation_endpoint";
    private static final String JWKS_URI = "jwks_uri";
    private static final String END_SESSION_ENDPOINT = "end_session_endpoint";
    private static final String RESPONSE_TYPES_SUPPORTED = "response_types_supported";
    private static final String RESPONSE_MODES_SUPPORTED = "response_modes_supported";
    private static final String SUBJECT_TYPES_SUPPORTED = "subject_types_supported";
    private static final String ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED = "id_token_signing_alg_values_supported";
    private static final String SCOPES_SUPPORTED = "scopes_supported";
    private static final String TOKEN_ENDPOINT_AUTH_METHODS_SUPPORTED = "token_endpoint_auth_methods_supported";
    private static final String CLAIMS_SUPPORTED = "claims_supported";

    private OAuth2AuthorizationServerInterceptor oasi;

    private String authorizationEndpoint;
    private String issuer;
    private String tokenEndpoint;
    private String userinfoEndpoint;
    private String revocationEndpoint;
    private String jwksUri;
    private String endSessionEndpoint;
    private String supportedResponseTypes;
    private String supportedResponseModes;
    private String supportedSubjectType;
    private String supportedIdTokenSigningAlgValues;
    private String supportedScopes;
    private String supportedTokenEndpointAuthMethods;
    private String supportedClaims;


    public void init(OAuth2AuthorizationServerInterceptor oasi) throws IOException {
        this.oasi = oasi;
        getValuesFromOasi();
    }

    public void init() throws IOException {
        init(null);
    }

    private void getValuesFromOasi() {
        if(oasi == null)
            return;
        String baseOauth2Url = ResolverMap.combine(oasi.getIssuer() + "/", "oauth2/");

        setIssuer(oasi.getIssuer());
        setAuthorizationEndpoint(baseOauth2Url + "auth");
        setTokenEndpoint(baseOauth2Url + "token");
        setUserinfoEndpoint(baseOauth2Url + "userinfo");
        setRevocationEndpoint(oasi.getTokenGenerator().supportsRevocation() ? baseOauth2Url + "revoke" : null);
        setJwksUri(baseOauth2Url + "certs");
        setSupportedResponseTypes(oasi.getSupportedAuthorizationGrants());
        setSupportedResponseModes("query fragment");
        setSupportedSubjectType("public");
        setSupportedIdTokenSigningAlgValues("RS256");
        setSupportedScopes(oasi.getClaimList().getSupportedScopes());
        setSupportedTokenEndpointAuthMethods("client_secret_post");
        setSupportedClaims(oasi.getClaimList().getSupportedClaimsAsString());
    }

    public String getWellknown() {
        try (var bufferedJsonGenerator = new BufferedJsonGenerator()) {
            var jg = bufferedJsonGenerator.getJsonGenerator();
            jg.writeStartObject();

            jg.writeObjectField(ISSUER, getIssuer());
            jg.writeObjectField(AUTHORIZATION_ENDPOINT, getAuthorizationEndpoint());
            jg.writeObjectField(TOKEN_ENDPOINT, getTokenEndpoint());
            jg.writeObjectField(USERINFO_ENDPOINT, getUserinfoEndpoint());
            String revocationEndpoint1 = getRevocationEndpoint();
            if (revocationEndpoint1 != null)
                jg.writeObjectField(REVOCATION_ENDPOINT, revocationEndpoint1);
            jg.writeObjectField(JWKS_URI, getJwksUri());
            if (getEndSessionEndpoint() != null)
                jg.writeObjectField(END_SESSION_ENDPOINT, getEndSessionEndpoint());
            stringEnumToJson(jg, RESPONSE_TYPES_SUPPORTED, getSupportedResponseTypes().split(" "));
            if (supportedResponseModes != null)
                stringEnumToJson(jg, RESPONSE_MODES_SUPPORTED, getSupportedResponseModes().split(" "));
            stringEnumToJson(jg, SUBJECT_TYPES_SUPPORTED, getSupportedSubjectType().split(" "));
            stringEnumToJson(jg, ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED, getSupportedIdTokenSigningAlgValues().split(" "));
            stringEnumToJson(jg, SCOPES_SUPPORTED, getSupportedScopes().split(" "));
            stringEnumToJson(jg, TOKEN_ENDPOINT_AUTH_METHODS_SUPPORTED, getSupportedTokenEndpointAuthMethods().split(" "));
            stringEnumToJson(jg, CLAIMS_SUPPORTED, getSupportedClaims().split(" "));

            jg.writeEndObject();
            return bufferedJsonGenerator.getJson();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void stringEnumToJson(JsonGenerator jg, String name, String... enumeration) throws IOException {
        jg.writeArrayFieldStart(name);
        for(String value : enumeration)
            jg.writeString(OAuth2Util.urldecode(value));
        jg.writeEndArray();
    }

    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    public void setAuthorizationEndpoint(String authorizationEndpoint) {
        this.authorizationEndpoint = authorizationEndpoint;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getUserinfoEndpoint() {
        return userinfoEndpoint;
    }

    public void setUserinfoEndpoint(String userinfoEndpoint) {
        this.userinfoEndpoint = userinfoEndpoint;
    }

    public String getRevocationEndpoint() {
        return revocationEndpoint;
    }

    public void setRevocationEndpoint(String revocationEndpoint) {
        this.revocationEndpoint = revocationEndpoint;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getEndSessionEndpoint() {
        return endSessionEndpoint;
    }

    public void setEndSessionEndpoint(String endSessionEndpoint) {
        this.endSessionEndpoint = endSessionEndpoint;
    }

    public String getSupportedResponseTypes() {
        return supportedResponseTypes;
    }

    public void setSupportedResponseTypes(Set<String> supportedResponseTypes) {
        setSupportedResponseTypes(supportedResponseTypes.stream()
                .map(OAuth2Util::urlencode)
                .collect(joining(" ")));
    }

    public void setSupportedResponseTypes(String supportedResponseTypes) {
        this.supportedResponseTypes = supportedResponseTypes;
    }

    public String getSupportedResponseModes() {
        return supportedResponseModes;
    }

    public void setSupportedResponseModes(Set<String> supportedResponseModes) {
        setSupportedResponseModes(supportedResponseModes.stream()
                .map(OAuth2Util::urlencode)
                .collect(joining(" ")));
    }

    public void setSupportedResponseModes(String supportedResponseModes) {
        this.supportedResponseModes = supportedResponseModes;
    }

    public String getSupportedSubjectType() {
        return supportedSubjectType;
    }

    public void setSupportedSubjectType(String supportedSubjectType) {
        this.supportedSubjectType = supportedSubjectType;
    }

    public String getSupportedIdTokenSigningAlgValues() {
        return supportedIdTokenSigningAlgValues;
    }

    public void setSupportedIdTokenSigningAlgValues(String supportedIdTokenSigningAlgValues) {
        this.supportedIdTokenSigningAlgValues = supportedIdTokenSigningAlgValues;
    }

    public String getSupportedScopes() {
        return supportedScopes;
    }

    public void setSupportedScopes(String supportedScopes) {
        this.supportedScopes = supportedScopes;
    }

    public String getSupportedTokenEndpointAuthMethods() {
        return supportedTokenEndpointAuthMethods;
    }

    public void setSupportedTokenEndpointAuthMethods(String supportedTokenEndpointAuthMethods) {
        this.supportedTokenEndpointAuthMethods = supportedTokenEndpointAuthMethods;
    }

    public String getSupportedClaims() {
        return supportedClaims;
    }

    public void setSupportedClaims(String supportedClaims) {
        this.supportedClaims = supportedClaims;
    }

    public void setSupportedClaims(Set<String> supportedClaims) {
        setSupportedClaims(supportedClaims.stream()
                .map(OAuth2Util::urlencode)
                .collect(joining(" "))
        );
    }
}
