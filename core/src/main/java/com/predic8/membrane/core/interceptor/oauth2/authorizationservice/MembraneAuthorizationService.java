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
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.oauth2.ClaimRenamer;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Util;
import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import org.apache.commons.io.IOUtils;
import com.predic8.membrane.annot.Required;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@MCElement(name="membrane")
public class MembraneAuthorizationService extends AuthorizationService {
    private String src; // url to OpenID-Provider data

    private String tokenEndpoint;
    private String userInfoEndpoint;
    private String subject = ClaimRenamer.convert("sub");
    private String authorizationEndpoint;
    private String publicAuthorizationEndpoint;
    private String revocationEndpoint;
    private String registrationEndpoint;
    private String jwksEndpoint;
    private String claims;
    private String claimsIdt;
    private String claimsParameter;

    private DynamicRegistration dynamicRegistration;

    public static boolean isValidURI(String uri)
    {
        try
        {
            new URI(uri);
            return true;
        } catch (Exception exception)
        {
            return false;
        }
    }

    @Override
    public void init() throws Exception {
        if(src == null)
            throw new Exception("No wellknown file source configured. - Cannot work without one");
        if(dynamicRegistration != null){
            dynamicRegistration.init(router);
            supportsDynamicRegistration = true;
        }
        try {
            String[] urls = src.split(Pattern.quote(" "),2);
            if(urls.length == 1) {
                String url = urls[0] + (urls[0].endsWith("/") ? "" : "/") + ".well-known/openid-configuration";

                parseSrc(dynamicRegistration != null ?
                        dynamicRegistration.retrieveOpenIDConfiguration(url) :
                        router.getResolverMap().resolve(url));
            }
            else if(urls.length == 2){
                String internalUrl = urls[1] + (urls[1].endsWith("/") ? "" : "/") + ".well-known/openid-configuration";

                parseSrc(dynamicRegistration != null ?
                        dynamicRegistration.retrieveOpenIDConfiguration(internalUrl) :
                        router.getResolverMap().resolve(internalUrl));

                publicAuthorizationEndpoint = urls[0] + new URI(authorizationEndpoint).getPath();
            }
            else if(urls.length > 2)
                throw new RuntimeException("src property is not set correctly: " + src);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        adjustScope();
        prepareClaimsForLoginUrl();
    }

    @Override
    public String getIssuer() {
        int p = src.indexOf(' ');
        if (p != -1) {
            String[] urls = src.split(Pattern.quote(" "),2);
            p = urls[1].indexOf('/');
            return urls[0] + (p == -1 ? "" : urls[1].substring(p));
        }
        return src;
    }

    @Override
    public String getJwksEndpoint() throws Exception {
        return jwksEndpoint;
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

    private void adjustScope() throws UnsupportedEncodingException {
        if(scope == null)
            scope = "profile";
        scope = OAuth2Util.urlencode(scope);
    }

    private void parseSrc(InputStream resolve) throws IOException {
        String file = IOUtils.toString(resolve);
        ObjectMapper mapper = new ObjectMapper();
        Map<String,Object> json = mapper.readValue(file, new TypeReference<>() {});

        // without checks
        tokenEndpoint = (String) json.get("token_endpoint");
        userInfoEndpoint = (String) json.get("userinfo_endpoint");
        authorizationEndpoint = (String) json.get("authorization_endpoint");
        revocationEndpoint = (String) json.get("revocation_endpoint");
        registrationEndpoint = (String) json.get("registration_endpoint");
        jwksEndpoint = (String) json.get("jwks_uri");

    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    @Override
    public String getRevocationEndpoint() {
        return revocationEndpoint;
    }

    @Override
    public String getLoginURL(String securityToken, String callbackURL, String pathQuery) {
        String endpoint = publicAuthorizationEndpoint;
        if(endpoint == null)
            endpoint = authorizationEndpoint;
        return endpoint +"?"+
                "client_id=" + getClientId() + "&"+
                "response_type=code&"+
                "scope="+scope+"&"+
                "redirect_uri=" + callbackURL + "&"+
                "state=security_token%3D" + securityToken + "%26url%3D" + OAuth2Util.urlencode(pathQuery) +
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

    @Required
    @MCAttribute
    public void setSrc(String src) {
        this.src = src;
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
}
