/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.cors.AbstractCORSHandler.ResponseHeaderBuilder.responseBuilder;

/**
 * Handles CORS response processing by setting appropriate CORS headers
 * on the response when the origin is allowed or when all origins are permitted.
 * <p>
 * This handler is typically used in the response phase of CORS processing
 * to add necessary headers like Access-Control-Allow-Origin,
 * Access-Control-Allow-Credentials, etc.
 */
public class ResponseHandler extends AbstractCORSHandler {

    public ResponseHandler(CorsInterceptor interceptor) {
        super(interceptor);
    }

    @Override
    public Outcome handleInternal(Exchange exc, String origin) {

        if (interceptor.isAllowAll() || originAllowed(origin)) {
            setCORSHeader(exc, origin);
        }

        // Not allowed => Do not set any allow headers
        return CONTINUE;
    }

    @Override
    protected String getRequestMethod(Exchange exc) {
        return exc.getRequest().getMethod();
    }

    protected void setCORSHeader(Exchange exc, String requestOrigin) {
        responseBuilder(exc)
                .allowOrigin(determineAllowOriginHeader(requestOrigin))
                .exposeHeaders(interceptor.getExposeHeaders())
                .allowCredentials(interceptor.getCredentials())
                .build();
    }

}
