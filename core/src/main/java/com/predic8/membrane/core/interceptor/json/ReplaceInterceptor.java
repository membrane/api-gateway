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

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@SuppressWarnings("unused")
@MCElement(name="replace")
public class ReplaceInterceptor extends AbstractInterceptor {

    private static Logger log = LoggerFactory.getLogger(ReplaceInterceptor.class);

    private String jsonPath;

    private String with;

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleInternal(exc.getRequestContentType(), exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleInternal(exc.getResponseContentType(), exc.getResponse());
    }

    private Outcome handleInternal(String contentType, Message msg) {
        if(contentType.equals(APPLICATION_JSON)) {
            msg.setBodyContent(replaceWithJsonPath(msg, jsonPath, with).getBytes());
        }
        return CONTINUE;
    }

     String replaceWithJsonPath(Message msg, String jsonPath, String replacement) {
         Object document = Configuration.defaultConfiguration().jsonProvider().parse(msg.getBodyAsStringDecoded());
         document = JsonPath.parse(document).set(jsonPath, replacement).json();
         return Configuration.defaultConfiguration().jsonProvider().toJson(document);
    }

    /**
     * Sets the JSONPath expression to identify the target node in the JSON structure.
     *
     * @param jsonPath the JSONPath expression (e.g., "$.person.name").
     */
    @MCAttribute
    public void setJsonPath(String jsonPath) {
        this.jsonPath = jsonPath;
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

    public String getJsonPath() {return jsonPath;}

    public String getWith() {return with;}


}
