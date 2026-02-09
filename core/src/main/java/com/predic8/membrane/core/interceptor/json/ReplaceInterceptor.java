/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.json;

import com.jayway.jsonpath.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.lang.AbstractExchangeExpressionInterceptor;
import org.slf4j.*;

import static com.jayway.jsonpath.Configuration.defaultConfiguration;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * @description Replaces a JSON value at the configured JSONPath with a static string.
 * @yaml
 * <pre><code>
 *  api:
 *    flow:
 *      - replace:
 *          jsonPath: $.person.name
 *          with: Alice
 * </code></pre>
 */
@SuppressWarnings("unused")
@MCElement(name="replace")
public class ReplaceInterceptor extends AbstractExchangeExpressionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ReplaceInterceptor.class);

    private String with;

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    private Outcome handleInternal(Exchange exc, Flow flow) {
        Message msg = exc.getMessage(flow);
        String expr = exchangeExpression.evaluate(exc, flow, String.class);

        switch (language) {
            case JSONPATH -> handleJsonPath(msg, expr);
            case XPATH -> handleXPath(msg, expr);
        }
        return CONTINUE;
    }

    private void handleXPath(Message message, String expr) {

    }

    private void handleJsonPath(Message msg, String jsonPath) {
        if (!isJson(msg.getHeader().getContentType())) return;
        Object document = defaultConfiguration().jsonProvider().parse(msg.getBodyAsStringDecoded());
        document = JsonPath.parse(document).set(jsonPath, with).json();
        msg.setBodyContent(defaultConfiguration().jsonProvider().toJson(document).getBytes(UTF_8));
    }

    /**
     * Sets the JSONPath expression to identify the target node in the JSON structure.
     *
     * @param expr the JSONPath expression (e.g., "$.person.name").
     */
    @MCAttribute
    public void setExpression(String expr) {
        expression = expr;
    }

    /**
     * Sets the replacement value for the node specified by the JSONPath.
     *
     * @param with the new value to replace the existing one.
     */
    @MCAttribute
    public void setWith(String with) {
        this.with = with;
    }

    public String getExpression() {return expression;}

    public String getWith() {return with;}


}
