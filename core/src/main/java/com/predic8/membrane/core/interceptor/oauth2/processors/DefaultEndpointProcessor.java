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

package com.predic8.membrane.core.interceptor.oauth2.processors;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Util;

public class DefaultEndpointProcessor extends EndpointProcessor {
    public DefaultEndpointProcessor(OAuth2AuthorizationServerInterceptor authServer) {
        super(authServer);
    }

    @Override
    public boolean isResponsible(Exchange exc) {
        return true;
    }

    @Override
    public Outcome process(Exchange exc) throws Exception {
    	if (exc.getResponse() == null) {
            exc.setResponse(OAuth2Util.createParameterizedJsonErrorResponse(jsonGen, "error", "invalid_request"));
    	}
        return Outcome.RETURN;
    }
}
