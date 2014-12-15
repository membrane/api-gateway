/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.api;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;;

import java.lang.Override;

/**
 * A custom interceptor which adds the header 'X-Hello' to the HTTP request.
 *
 * @author Oliver Weiler
 */
public class AddMyHeaderInterceptor extends AbstractInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AddMyHeaderInterceptor.class);

    @Override
    public Outcome handleRequest(Exchange exchange) {
        log.info(exchange.getRequest().getHeader().getUserAgent());

        exchange.getRequest().getHeader().add("X-Hello", "Hello World!");

        log.info(exchange.getRequest());

        return Outcome.CONTINUE;
    }
}
