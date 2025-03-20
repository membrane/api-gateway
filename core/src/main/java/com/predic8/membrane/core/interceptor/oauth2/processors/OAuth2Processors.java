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
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.Outcome;

import java.util.ArrayList;

public class OAuth2Processors {
    final ArrayList<EndpointProcessor> processors = new ArrayList<>();

    public OAuth2Processors add(EndpointProcessor excProc){
        processors.add(excProc);
        return this;
    }

    public Outcome runProcessors(Exchange exc) {
        for(EndpointProcessor excProc : processors){
            if(excProc.isResponsible(exc)) {
                Outcome result = excProc.process(exc);
                postProcessing(exc);
                return result;
            }
        }
        throw new RuntimeException("No OAuthEndpointProcessor found. This should never happen!");
    }

    private void postProcessing(Exchange exc){
        addAccessControlAllowOriginHeader(exc);
    }

    private void addAccessControlAllowOriginHeader(Exchange exc) {
        String origin = exc.getRequest().getHeader().getFirstValue(Header.ORIGIN);
        if(origin == null || origin.isEmpty())
            return;
        exc.getResponse().getHeader().add(Header.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        exc.getResponse().getHeader().add("Access-Control-Allow-Credentials", "true");
    }
}
