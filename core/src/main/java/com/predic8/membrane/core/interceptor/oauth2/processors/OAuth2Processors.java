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

import java.util.ArrayList;

public class OAuth2Processors {
    ArrayList<EndpointProcessor> processors = new ArrayList<EndpointProcessor>();

    public OAuth2Processors add(EndpointProcessor excProc){
        processors.add(excProc);

        return this;
    }

    public Outcome runProcessors(Exchange exc) throws Exception {
        for(EndpointProcessor excProc : processors){
            if(excProc.isResponsible(exc)) {
                return excProc.process(exc);
            }
        }
        throw new RuntimeException("No OAuthEndpointProcessor found. This should never happen!");
    }
}
