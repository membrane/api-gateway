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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;

import java.util.List;

@MCElement(name="client", component =false, id="staticClientList-client")
public class Client {
    private String clientId;
    private String clientSecret;
    private String callbackUrl;
    private String grantTypes = "authorization_code,password,client_credentials,refresh_token,implicit";
    private String resources;
    private String scopes;

    public Client(){
    }

    public Client(String clientId, String clientSecret, String callbackUrl, String grantTypes){
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackUrl = callbackUrl;
        this.setGrantTypes(grantTypes); 
    }

    public boolean verify(String clientId, String clientSecret){
        if(!this.clientId.equals(clientId) || !this.clientSecret.equals(clientSecret))
            return false;
        return true;
    }

    public String getClientId() {
        return clientId;
    }

    @Required
    @MCAttribute
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    @Required
    @MCAttribute
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    @MCAttribute
    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

	public String getGrantTypes() {
		return grantTypes;
	}

    /**
     * @description Comma separated list of grant types this client can be used with.
     * @default authorization_code,password,client_credentials,refresh_token,implicit
     */
    @MCAttribute
	public void setGrantTypes(String grantTypes) {
		this.grantTypes = grantTypes;
	}

    public String getResources() {
        return resources;
    }

    /**
     * @description Space separated list of resource URIs (audiences) this client may request tokens for,
     *              see RFC 8707. In the client_credentials grant, the "resource" request parameter is
     *              checked against this list, and the granted resources become the "aud" claim of the
     *              issued token (requires a JWT token generator such as bearerJwtToken). If the request
     *              contains no "resource" parameter, all resources listed here are granted.
     */
    @MCAttribute
    public void setResources(String resources) {
        this.resources = resources;
    }

    public List<String> getResourceList() {
        return splitList(resources);
    }

    public String getScopes() {
        return scopes;
    }

    /**
     * @description Space separated list of scopes this client may request in the client_credentials
     *              grant. The "scope" request parameter is checked against this list, and the granted
     *              scopes become the "scope" claim of the issued token (requires a JWT token generator
     *              such as bearerJwtToken). If the request contains no "scope" parameter, all scopes
     *              listed here are granted. Without this attribute, the "scope" parameter is passed
     *              through unchecked and no "scope" claim is issued.
     */
    @MCAttribute
    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public List<String> getScopeList() {
        return splitList(scopes);
    }

    private static List<String> splitList(String value) {
        if (value == null || value.isBlank())
            return List.of();
        return List.of(value.trim().split(" +"));
    }
}
