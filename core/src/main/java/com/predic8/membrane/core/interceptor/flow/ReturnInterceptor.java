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

package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_RESPONSE_ABORT_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.util.HttpUtil.getMessageForStatusCode;
import static java.lang.String.format;


/**
 * @description Stops processing the request and sends a response back to the client without calling
 * the backend. If the exchange already holds a response, that response is sent; otherwise a new
 * response is built from the request's body and Content-Type. Often paired with <code>template</code>
 * to return a generated body. See the examples under examples/templating.
 * @topic 1. Proxies and Flow
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - template:
 *         contentType: application/json
 *         src: '{ "status": "ok" }'
 *     - return:
 *         status: 200
 * </code></pre>
 */
@MCElement(name = "return")
public class ReturnInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ReturnInterceptor.class.getName());

    private int status = 200;
    private String contentType = null;

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            exc.setResponse(getOrCreateResponse(exc));
        } catch (IOException e) {
            String detail = "Could not create response!";
            log.error(detail, e);
            internal(router.getConfiguration().isProduction(),getDisplayName())
                    .detail(detail)
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
        return RETURN;
    }

    private Response getOrCreateResponse(Exchange exc) throws IOException {
        Response response = exc.getResponse();
        if (response == null) {
            response = createResponseFromRequest(exc);
        }

        if (status != 0) {
            response.setStatusCode(status);
            response.setStatusMessage(getMessageForStatusCode(status));
        }

        if (contentType!=null) {
            response.getHeader().setContentType(contentType);
        }

        if(response.isBodyEmpty() && !response.getHeader().hasContentLength()) {
            response.getHeader().setContentLength(0);
        }
        return response;
    }

    private Response createResponseFromRequest(Exchange exc) throws IOException {
        Response.ResponseBuilder builder = new Response.ResponseBuilder().status(status);
        String reqContentType = exc.getRequest().getHeader().getContentType();
        if (reqContentType != null) {
            builder.contentType(reqContentType);
        }
        Response response = builder.build();
        if (exc.getRequest().getBody() instanceof Body body) {
            response.setBody(body);
            response.getHeader().setContentLength(body.getLength());
        }
        return response;
    }

    @Override
    public String getDisplayName() {
        return "return";
    }

    @Override
    public String getShortDescription() {
        return (contentType != null) ? format("Sends a response with a status code of %d and a content type of %s.", status, contentType) : format("Sends a response with a status code of %d.", status);
    }

    @Override
    public EnumSet<Flow> getAppliedFlow() {
        return REQUEST_RESPONSE_ABORT_FLOW;
    }

    /**
     * @deprecated Use status instead.
     * @description Deprecated alias for <code>status</code>, kept for backward compatibility.
     * @default 200
     * @example 400
     */
    @Deprecated
    @MCAttribute(excludeFromJson = true)
    public void setStatusCode(int statusCode) {
        // Is excluded from the JSON schema. But will be included in the XSD schema.
        this.status = statusCode;
    }

    public int getStatusCode() {
        return status;
    }

    /**
     * @description HTTP status code to be returned.
     * @default 200
     * @example 400
     */
    @MCAttribute
    public void setStatus(int status) {
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    /**
     * @description Content-Type of the response. When unset, an existing response's Content-Type is left unchanged; only
     * when a new response is built from the request is the request's Content-Type used if present, otherwise none is set.
     * @default null
     * @example application/json; charset=utf-8
     */
    @MCAttribute
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }
}
