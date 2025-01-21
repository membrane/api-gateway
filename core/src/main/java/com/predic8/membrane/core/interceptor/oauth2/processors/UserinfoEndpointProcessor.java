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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.request.*;
import org.slf4j.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

public class UserinfoEndpointProcessor extends EndpointProcessor {

    private static final Logger log = LoggerFactory.getLogger(UserinfoEndpointProcessor.class);

    public UserinfoEndpointProcessor(OAuth2AuthorizationServerInterceptor authServer) {
        super(authServer);
    }

    @Override
    public boolean isResponsible(Exchange exc) {
        return exc.getRequestURI().startsWith(authServer.getBasePath() + "/oauth2/userinfo");
    }

    @Override
    public Outcome process(Exchange exc) {
        try {
            exc.setResponse(new UserinfoRequest(authServer,exc).validateRequest());
        } catch (Exception e) {
            log.error("", e);
            internal(true,"user-info-endpoint-processor")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
        if(exc.getResponse().getStatusCode() == 200)
            authServer.getStatistics().accessTokenValid();
        else
            authServer.getStatistics().accessTokenInvalid();
        return Outcome.RETURN;
    }
}
