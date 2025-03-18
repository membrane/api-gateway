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
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.resolver.ResolverMap;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.stream.Collectors;

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

    private String wellknown;
    private OAuth2AuthorizationServerInterceptor oasi;
    private ResolverMap resolver;

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



    public void init(Router router, OAuth2AuthorizationServerInterceptor oasi) throws IOException {
        this.resolver = router.getResolverMap();
        this.oasi = oasi;
        getValuesFromOasi();
        writeWellknown();
    }

    public void init(Router router) throws IOException {
        init(router, null);
    }

    private String getOauth2Issuer(){
        return oasi.getIssuer();
    }

    private String baseOauth2Url(){
        return ResolverMap.combine(getOauth2Issuer() + "/","oauth2/");
    }

    private void getValuesFromOasi() {
        if(oasi == null)
            return;

        setIssuer(getOauth2Issuer());
        setAuthorizationEndpoint(baseOauth2Url() + "auth");
        setTokenEndpoint(baseOauth2Url() + "token");
        setUserinfoEndpoint(baseOauth2Url() + "userinfo");
        setRevocationEndpoint(oasi.getTokenGenerator().supportsRevocation() ? baseOauth2Url() + "revoke" : null);
        setJwksUri(baseOauth2Url() + "certs");
        setSupportedResponseTypes(oasi.getSupportedAuthorizationGrants());
        setSupportedResponseModes("query fragment");
        setSupportedSubjectType("public");
        setSupportedIdTokenSigningAlgValues("RS256");
        setSupportedScopes(getSupportedOasiScopes());
        setSupportedTokenEndpointAuthMethods("client_secret_post");
        setSupportedClaims(getSupportedOasiClaims());
    }

    private String getSupportedOasiClaims() {
        return oasi.getClaimList().getSupportedClaimsAsString();
    }

    private String getSupportedOasiScopes() {
        return oasi.getClaimList().getSupportedScopes();
    }

    private void writeWellknown() throws IOException {
        try (var bufferedJsonGenerator = new BufferedJsonGenerator()) {
            var jg = bufferedJsonGenerator.getJsonGenerator();
            jg.writeStartObject();

            writeIssuer(jg);
            writeAuthorizationEndpoint(jg);
            writeTokenEndpoint(jg);
            writeUserinfoEndpoint(jg);
            writeRevocationEndpoint(jg);
            writeJwksUri(jg);
            writeEndSessionEndpoint(jg);
            writeSupportedResponseTypes(jg);
            writeSupportedResponseModes(jg);
            writeSupportedSubjectTypes(jg);
            writeSupportedIdTokenSigningAlgValues(jg);
            writeSupportedScopes(jg);
            writeSupportedTokenEndpointAuthMethods(jg);
            writeSupportedClaims(jg);

            jg.writeEndObject();
            setWellknown(bufferedJsonGenerator.getJson());
        }
    }

    private void writeSupportedClaims(JsonGenerator jg) throws IOException {
        stringEnumToJson(jg, CLAIMS_SUPPORTED, getSupportedClaims().split(" "));
    }

    private void writeSupportedTokenEndpointAuthMethods(JsonGenerator jg) throws IOException {
        stringEnumToJson(jg, TOKEN_ENDPOINT_AUTH_METHODS_SUPPORTED, getSupportedTokenEndpointAuthMethods().split(" "));
    }

    private void writeSupportedScopes(JsonGenerator jg) throws IOException {
        stringEnumToJson(jg, SCOPES_SUPPORTED, getSupportedScopes().split(" "));
    }

    private void writeSupportedIdTokenSigningAlgValues(JsonGenerator jg) throws IOException {
        stringEnumToJson(jg, ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED, getSupportedIdTokenSigningAlgValues().split(" "));
    }

    private void writeSupportedSubjectTypes(JsonGenerator jg) throws IOException {
        stringEnumToJson(jg, SUBJECT_TYPES_SUPPORTED, getSupportedSubjectType().split(" "));
    }

    private void stringEnumToJson(JsonGenerator jg, String name, String... enumeration) throws IOException {
        jg.writeArrayFieldStart(name);
        for(String value : enumeration)
            jg.writeString(OAuth2Util.urldecode(value));
        jg.writeEndArray();
    }

    private void writeSupportedResponseTypes(JsonGenerator jg) throws IOException {
        stringEnumToJson(jg, RESPONSE_TYPES_SUPPORTED, getSupportedResponseTypes().split(" "));
    }

    private void writeSupportedResponseModes(JsonGenerator jg) throws IOException {
        if (supportedResponseModes != null)
            stringEnumToJson(jg, RESPONSE_MODES_SUPPORTED, getSupportedResponseModes().split(" "));
    }

    private void writeJwksUri(JsonGenerator jg) throws IOException {
        writeSingleJsonField(jg, JWKS_URI, getJwksUri());
    }

    private void writeEndSessionEndpoint(JsonGenerator jg) throws IOException {
        if (getEndSessionEndpoint() != null)
            writeSingleJsonField(jg, END_SESSION_ENDPOINT, getEndSessionEndpoint());
    }

    private void writeSingleJsonField(JsonGenerator jg, String name, String value) throws IOException {
        jg.writeObjectField(name, value);
    }

    private void writeRevocationEndpoint(JsonGenerator jg) throws IOException {
        String revocationEndpoint1 = getRevocationEndpoint();
        if (revocationEndpoint1 != null)
            writeSingleJsonField(jg, REVOCATION_ENDPOINT, revocationEndpoint1);
    }

    private void writeUserinfoEndpoint(JsonGenerator jg) throws IOException {
        writeSingleJsonField(jg, USERINFO_ENDPOINT, getUserinfoEndpoint());
    }

    private void writeTokenEndpoint(JsonGenerator jg) throws IOException {
        writeSingleJsonField(jg, TOKEN_ENDPOINT, getTokenEndpoint());
    }

    private void writeAuthorizationEndpoint(JsonGenerator jg) throws IOException {
        writeSingleJsonField(jg, AUTHORIZATION_ENDPOINT, getAuthorizationEndpoint());
    }

    private void writeIssuer(JsonGenerator jg) throws IOException {
        writeSingleJsonField(jg, ISSUER, getIssuer());
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
        StringBuilder builder = new StringBuilder();
        for(String resp : supportedResponseTypes)
            builder.append(" ").append(OAuth2Util.urlencode(resp));
        setSupportedResponseTypes(builder.toString().trim());
    }

    public void setSupportedResponseTypes(String supportedResponseTypes) {
        this.supportedResponseTypes = supportedResponseTypes;
    }

    public String getSupportedResponseModes() {
        return supportedResponseModes;
    }

    public void setSupportedResponseModes(Set<String> supportedResponseModes) {
        StringBuilder builder = new StringBuilder();
        for(String resp : supportedResponseModes)
            builder.append(" ").append(OAuth2Util.urlencode(resp));
        setSupportedResponseModes(builder.toString().trim());
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
                .collect(Collectors.joining(" "))
        );
    }

    public String getWellknown() {
        return wellknown;
    }

    public void setWellknown(String wellknown) {
        this.wellknown = wellknown;
    }
}
