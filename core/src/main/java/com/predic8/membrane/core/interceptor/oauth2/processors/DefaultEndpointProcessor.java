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
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

public class DefaultEndpointProcessor extends EndpointProcessor {

    private static final Logger log = LoggerFactory.getLogger(DefaultEndpointProcessor.class);

    public DefaultEndpointProcessor(OAuth2AuthorizationServerInterceptor authServer) {
        super(authServer);
    }

    @Override
    public boolean isResponsible(Exchange exc) {
        return true;
    }

    @Override
    public Outcome process(Exchange exc) {
    	if (exc.getResponse() == null) {
            try {
                exc.setResponse(OAuth2Util.createParameterizedJsonErrorResponse("error", "invalid_request"));
            } catch (IOException e) {
                log.error("", e);
                internal(true,"endpoint-processor")
                        .component(this.getClass().getSimpleName())
                        .exception(e)
                        .buildAndSetResponse(exc);
                return ABORT;
            }
        }
        return RETURN;
    }
}
