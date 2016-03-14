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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.oauth2.parameter.ClaimsParameter;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Required;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;

@MCElement(name="membrane")
public class MembraneAuthorizationService extends AuthorizationService {
    private String src; // url to wellknown data

    private String tokenEndpoint;
    private String userInfoEndpoint;
    private String subject = "username";
    private String authorizationEndpoint;
    private String revocationEndpoint;
    private String claims;
    private String claimsIdt;
    private String claimsParameter;


    @Override
    protected void init() throws Exception {
        if(src == null)
            throw new Exception("No wellknown file source configured. - Cannot work without one");
        try {
            parseSrc(router.getResolverMap().resolve(src + "/.well-known/openid-configuration"));
        } catch (ResourceRetrievalException e) {
            throw new RuntimeException(e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
        adjustScope();
        prepareClaimsForLoginUrl();
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
        Map<String,Object> json = mapper.readValue(file,Map.class);

        // without checks
        tokenEndpoint = (String) json.get("token_endpoint");
        userInfoEndpoint = (String) json.get("userinfo_endpoint");
        authorizationEndpoint = (String) json.get("authorization_endpoint");
        revocationEndpoint = (String) json.get("revocation_endpoint");
    }

    protected String getTokenEndpoint() {
        return tokenEndpoint;
    }

    @Override
    protected String getRevocationEndpoint() {
        return revocationEndpoint;
    }

    @Override
    protected String getLoginURL(String securityToken, String publicURL, String pathQuery) {
        return authorizationEndpoint +"?"+
                "client_id=" + getClientId() + "&"+
                "response_type=code&"+
                "scope="+scope+"&"+
                "redirect_uri=" + publicURL + "oauth2callback&"+
                "state=security_token%3D" + securityToken + "%26url%3D" + pathQuery +
                getClaimsParameter();
    }

    private String getClaimsParameter() {
        if(claimsParameter == null)
            return "";
        try {
            return "&claims=" + OAuth2Util.urlencode(claimsParameter);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    protected String getUserInfoEndpoint() {
        return userInfoEndpoint;
    }

    @Override
    protected String getSubject() {
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
}
