/*
 *  Copyright 2017 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;

import java.io.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name="methodOverride")
public class MethodOverrideInterceptor extends AbstractInterceptor {

    @Override
    public Outcome handleRequest(Exchange exc) {
        String methodHeader = exc.getRequest().getHeader().getFirstValue(Header.X_HTTP_METHOD_OVERRIDE);
        if(methodHeader == null)
            return CONTINUE;

        try {
            switch(methodHeader){
                case "GET": handleGet(exc);
                    break;
            }
        } catch (IOException e) {
            internal(router.isProduction(),getDisplayName())
                    .detail("Could overwrite method.")
                    .internal("method", methodHeader)
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }

        exc.getRequest().getHeader().removeFields(Header.X_HTTP_METHOD_OVERRIDE);

        return CONTINUE;
    }

    private void handleGet(Exchange exc) throws IOException {
        Request req = exc.getRequest();
        req.readBody();
        req.setBody(new EmptyBody());
        req.getHeader().removeFields(CONTENT_LENGTH);
        req.getHeader().removeFields(CONTENT_TYPE);
        req.getHeader().removeFields(CONTENT_ENCODING);
        req.setMethod("GET");
    }
}
