/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.rest;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.xml.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;

import java.nio.charset.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

// TODO IMPLEMENTATION NOT FINISHED
@MCElement(name = "http2xml")
public class HTTP2XMLInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HTTP2XMLInterceptor.class.getName());

    public HTTP2XMLInterceptor() {
        name = "http 2 xml";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            return handleRequestInternal(exc);
        } catch (Exception e) {
            log.error("",e);
            user(router.isProduction(),getDisplayName())
                    .detail("Could not generate XML from HTTP information!")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    public Outcome handleRequestInternal(Exchange exc) throws Exception {
        log.debug("uri: {}", exc.getRequest().getUri());

        String res = new Request(exc.getRequest()).toXml();
        log.debug("http-xml: {}", res);

        exc.getRequest().setBodyContent(res.getBytes(StandardCharsets.UTF_8));

        // TODO
        exc.getRequest().setMethod("POST");
        exc.getRequest().getHeader().setSOAPAction("");

        return Outcome.CONTINUE;
    }

}
