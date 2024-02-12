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
import java.util.HashSet;

public class WellknownFile {

    // taken from https://openid.net/specs/openid-connect-discovery-1_0.html
    private static final String ISSUER = "issuer";
    private static final String AUTHORIZATION_ENDPOINT = "authorization_endpoint";
    private static final String TOKEN_ENDPOINT = "token_endpoint";
    private static final String USERINFO_ENDPOINT = "userinfo_endpoint";
    private static final String REVOCATION_ENDPOINT = "revocation_endpoint";
    private static final String JWKS_URI = "jwks_uri";
    private static final String RESPONSE_TYPES_SUPPORTED = "response_types_supported";
    private static final String SUBJECT_TYPES_SUPPORTED = "subject_types_supported";
    private static final String ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED = "id_token_signing_alg_values_supported";
    private static final String SCOPES_SUPPORTED = "scopes_supported";
    private static final String TOKEN_ENDPOINT_AUTH_METHODS_SUPPORTED = "token_endpoint_auth_methods_supported";
    private static final String CLAIMS_SUPPORTED = "claims_supported";

    private String wellknown;
    private OAuth2AuthorizationServerInterceptor oasi;
    private ResolverMap resolver;
    private ReusableJsonGenerator reusableJsonGen = new ReusableJsonGenerator();
    private JsonGenerator jsonGen;

    private String authorizationEndpoint;
    private String issuer;
    private String tokenEndpoint;
    private String userinfoEndpoint;
    private String revocationEndpoint;
    private String jwksUri;
    private String supportedResponseTypes;
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
        return resolver.combine(getOauth2Issuer() + "/","oauth2/");
    }

    private void getValuesFromOasi() throws UnsupportedEncodingException {
        if(oasi == null)
            return;

        setIssuer(getOauth2Issuer());
        setAuthorizationEndpoint(baseOauth2Url() + "auth");
        setTokenEndpoint(baseOauth2Url() + "token");
        setUserinfoEndpoint(baseOauth2Url() + "userinfo");
        setRevocationEndpoint(baseOauth2Url() + "revoke");
        setJwksUri(baseOauth2Url() + "certs");
        setSupportedResponseTypes(oasi.getSupportedAuthorizationGrants());
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
        jsonGen = reusableJsonGen.resetAndGet();
        jsonGen.writeStartObject();

        writeIssuer();
        writeAuthorizationEndpoint();
        writeTokenEndpoint();
        writeUserinfoEndpoint();
        writeRevocationEndpoint();
        writeJwksUri();
        writeSupportedResponseTypes();
        writeSupportedSubjectTypes();
        writeSupportedIdTokenSigningAlgValues();
        writeSupportedScopes();
        writeSupportedTokenEndpointAuthMethods();
        writeSupportedClaims();

        jsonGen.writeEndObject();
        setWellknown(reusableJsonGen.getJson());
    }

    private void writeSupportedClaims() throws IOException {
        stringEnumToJson(CLAIMS_SUPPORTED, getSupportedClaims().split(" "));
    }

    private void writeSupportedTokenEndpointAuthMethods() throws IOException {
        stringEnumToJson(TOKEN_ENDPOINT_AUTH_METHODS_SUPPORTED, getSupportedTokenEndpointAuthMethods().split(" "));
    }

    private void writeSupportedScopes() throws IOException {
        stringEnumToJson(SCOPES_SUPPORTED, getSupportedScopes().split(" "));
    }

    private void writeSupportedIdTokenSigningAlgValues() throws IOException {
        stringEnumToJson(ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED, getSupportedIdTokenSigningAlgValues().split(" "));
    }

    private void writeSupportedSubjectTypes() throws IOException {
        stringEnumToJson(SUBJECT_TYPES_SUPPORTED, getSupportedSubjectType().split(" "));
    }

    private void stringEnumToJson(String name, String... enumeration) throws IOException {
        jsonGen.writeArrayFieldStart(name);
        for(String value : enumeration)
            jsonGen.writeString(OAuth2Util.urldecode(value));
        jsonGen.writeEndArray();
    }

    private void writeSupportedResponseTypes() throws IOException {
        stringEnumToJson(RESPONSE_TYPES_SUPPORTED,getSupportedResponseTypes().split(" "));
    }

    private void writeJwksUri() throws IOException {
        writeSingleJsonField(JWKS_URI, getJwksUri());
    }

    private void writeSingleJsonField(String name, String value) throws IOException {
        jsonGen.writeObjectField(name, value);
    }

    private void writeRevocationEndpoint() throws IOException {
        writeSingleJsonField(REVOCATION_ENDPOINT, getRevocationEndpoint());
    }

    private void writeUserinfoEndpoint() throws IOException {
        writeSingleJsonField(USERINFO_ENDPOINT, getUserinfoEndpoint());
    }

    private void writeTokenEndpoint() throws IOException {
        writeSingleJsonField(TOKEN_ENDPOINT, getTokenEndpoint());
    }

    private void writeAuthorizationEndpoint() throws IOException {
        writeSingleJsonField(AUTHORIZATION_ENDPOINT, getAuthorizationEndpoint());
    }

    private void writeIssuer() throws IOException {
        writeSingleJsonField(ISSUER, getIssuer());
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

    public String getSupportedResponseTypes() {
        return supportedResponseTypes;
    }

    public void setSupportedResponseTypes(HashSet<String> supportedResponseTypes) throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();
        for(String resp : supportedResponseTypes)
            builder.append(" ").append(OAuth2Util.urlencode(resp));
        setSupportedResponseTypes(builder.toString().trim());
    }

    public void setSupportedResponseTypes(String supportedResponseTypes) {
        this.supportedResponseTypes = supportedResponseTypes;
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

    public String getWellknown() {
        return wellknown;
    }

    public void setWellknown(String wellknown) {
        this.wellknown = wellknown;
    }
}
