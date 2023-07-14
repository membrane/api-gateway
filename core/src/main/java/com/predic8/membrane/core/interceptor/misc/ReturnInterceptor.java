/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.misc;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.HttpUtil.*;
import static java.lang.String.*;


/**
 * @description Terminates the exchange flow. The returned response is determined in the following order:
 * <p>
 * 1. If there is already a response in the exchange, that response is returned
 * 2. If there is no response in the exchange, the body and contentType of the request is copied into a new response.
 * </p>
 * <p>
 * The options statusCode and contentType will overwrite the values from the messages.
 * </p>
 * <p>
 * This plugin is useful together with the template plugin. See examples/template.
 * </p>
 * @topic 4. Interceptors/Features
 */
@MCElement(name = "return")
public class ReturnInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ReturnInterceptor.class.getName());

    private int statusCode = 200;
    private String contentType = null;

    @MCAttribute
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @MCAttribute
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    // @TODO Specify correct behaviour!
    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        exc.setResponse(getResponse(exc));
        return RETURN;
    }

    private Response getResponse(Exchange exc) throws IOException {
        Response response = exc.getResponse();
        if (response == null) {
            Response.ResponseBuilder builder = new Response.ResponseBuilder().status(statusCode);
            String reqContentType = exc.getRequest().getHeader().getContentType();
            if (reqContentType != null) {
                builder.contentType(reqContentType);
            }
            response = builder.build();
            if (exc.getRequest().getBody() instanceof Body body) {
                response.setBody(body);
                response.getHeader().setContentLength(body.getLength());
            }
        }

        if (statusCode != 0) {
            response.setStatusCode(statusCode);
            response.setStatusMessage(getMessageForStatusCode(statusCode));
        }

        if (contentType!=null) {
            response.getHeader().setContentType(contentType);
        }

        if(response.isBodyEmpty() && !response.getHeader().hasContentLength()) {
            response.getHeader().setContentLength(0);
        }
        return response;
    }

    @Override
    public String getDisplayName() {
        return "Return";
    }

    @Override
    public String getShortDescription() {
        return format("Sends an response with a status code of %d and an content type of %s.", statusCode, contentType);
    }
}
