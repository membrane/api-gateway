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

@MCElement(name="client", topLevel=false, id="staticClientList-client")
public class Client {
    private String clientId;
    private String clientSecret;
    private String callbackUrl;
    private String grantTypes = "authorization_code,password,client_credentials,refresh_token,implicit";

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

    @Required
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

}
