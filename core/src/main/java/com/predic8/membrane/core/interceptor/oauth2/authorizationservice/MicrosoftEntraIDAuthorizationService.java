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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Util;
import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Configure Membrane with Microsoft's Entra ID platform.
 */
@MCElement(name="microsoftEntraID")
public class MicrosoftEntraIDAuthorizationService extends AuthorizationService {
    private String tenantId;

    private String issuer;
    private String tokenEndpoint;
    private String userInfoEndpoint;
    private String authorizationEndpoint;
    private String revocationEndpoint;
    private String jwksEndpoint;
    private String endSessionEndpoint;
    private String claims;
    private String claimsIdt;
    private String claimsParameter;
    private List<String> responseModesSupported = List.of("query", "fragment");

    protected boolean encodedScope;

    @Override
    public void init() throws Exception {
        parseSrc(resolve(
                router.getResolverMap(),
                router.getBaseLocation(),
                getWellKnownUrl("https://login.microsoftonline.com/" + tenantId + "/v2.0/")));
        adjustScope();
        prepareClaimsForLoginUrl();
    }

    private @NotNull String getWellKnownUrl(String baseUrl) {
        return baseUrl + (baseUrl.endsWith("/") ? "" : "/") + ".well-known/openid-configuration";
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

    private void adjustScope() throws UnsupportedEncodingException {
        if(scope == null)
            scope = "openid";
        if (!encodedScope) {
            scope = OAuth2Util.urlencode(scope);
            encodedScope = true;
        }
    }

    private void parseSrc(InputStream resolve) throws IOException {
        String file = IOUtils.toString(resolve);
        ObjectMapper mapper = new ObjectMapper();
        Map<String,Object> json = mapper.readValue(file, new TypeReference<>() {});

        // without checks
        tokenEndpoint = (String) json.get("token_endpoint");
        if (tokenEndpoint == null)
            throw new RuntimeException("No token_endpoint could be detected.");
        userInfoEndpoint = (String) json.get("userinfo_endpoint");
        authorizationEndpoint = (String) json.get("authorization_endpoint");
        revocationEndpoint = (String) json.get("revocation_endpoint");
        jwksEndpoint = (String) json.get("jwks_uri");
        endSessionEndpoint = (String) json.get("end_session_endpoint");
        issuer = (String) json.get("issuer");
        if (json.containsKey("response_modes_supported")) {
            List<?> v = (List<?>) json.get("response_modes_supported");
            responseModesSupported = v.stream()
                    .filter(i -> i instanceof String)
                    .map(i -> (String)i)
                    .collect(Collectors.<String>toList());
        }
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
        boolean formPostSupported = responseModesSupported.contains("form_post");
        return authorizationEndpoint +"?"+
                "client_id=" + getClientId() + "&"+
                "response_type=code&"+
                "scope="+scope+"&"+
                "redirect_uri=" + callbackURL +
                (formPostSupported ? "&response_mode=form_post" : "") +
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
        return "sub";
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

    public List<String> getResponseModesSupported() {
        return responseModesSupported;
    }

    public String getTenantId() {
        return tenantId;
    }

    @Required
    @MCAttribute
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
