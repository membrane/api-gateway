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
package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.lang.*;
import org.slf4j.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

@MCElement(name="print")
public class PrintInterceptor extends AbstractLanguageInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PrintInterceptor.class.getName());

    String line;

    @Override
    public void init(Router router) throws Exception {
        super.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        handleInternal(exc, REQUEST);
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        handleInternal(exc, RESPONSE);
        return CONTINUE;
    }

    private void handleInternal(Exchange exc, Flow flow) {
        String s = exchangeExpression.evaluate(exc, flow, String.class);
        log.info(s);
    }

    public String getLine() {
        return line;
    }

    @MCAttribute
    public void setLine(String line) {
        this.line = line;
    }
}
