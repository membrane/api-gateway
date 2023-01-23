/* Copyright 2023 predic8 GmbH, www.predic8.com

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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import io.swagger.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

import static com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static java.util.EnumSet.of;

@MCElement(name = "jsonProtection")
public class JsonProtectionInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(JsonProtectionInterceptor.class);

    private ObjectMapper om = new ObjectMapper()
            .configure(FAIL_ON_READING_DUP_TREE_KEY, true)
            .configure(STRICT_DUPLICATE_DETECTION, true);

    private int maxTokens = 10000;

    public JsonProtectionInterceptor() {
        name = "JSON protection";
        setFlow(of(REQUEST));
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if ("GET".equals(exc.getRequest().getMethod()))
            return Outcome.CONTINUE;
        try {
            JsonParser parser = om.createParser(exc.getRequest().getBodyAsStreamDecoded());
            int tokenCount = 0;
            while (true) {
                JsonToken jsonToken = parser.nextValue();
                if (jsonToken == null)
                    break;
                tokenCount++;
                if (tokenCount > maxTokens)
                    throw new JsonParseException(parser, "Exceeded maxTokens (" + maxTokens + ").");
            }
        } catch (JsonParseException e) {
            LOG.error(e.getMessage());
            exc.setResponse(Response.badRequest().build());
            return Outcome.RETURN;
        }

        return Outcome.CONTINUE;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * Maximum number of tokens a JSON document may consist of. For example, <code>{"a":"b"}</code> counts as 3.
     * @param maxTokens
     */
    @MCAttribute
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public String getShortDescription() {
        return "Protects against several JSON attack classes.";
    }

    @Override
    public String getLongDescription() {
        return "<div>Enforces the following constraints:<br/><ul>" +
                "<li>HTTP request body must be well-formed JSON, if the HTTP verb is not" +
                "<font style=\"font-family: monospace\">GET</font>.</li>" +
                "<li>Limits the maximum number of tokens to " + maxTokens + ". (Each string and opening bracket counts" +
                "as a token: <font style=\"font-family: monospace\">{\"a\":\"b\"}</font> counts as 3 tokens)</li>" +
                "<li>Forbids duplicate keys. (<font style=\"font-family: monospace\">{\"a\":\"b\", \"a\":\"c\"}</font> " +
                "will be rejected.)</li>" +
                "</ul></div>";
    }
}
