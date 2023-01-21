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

import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.util.HttpUtil.getMessageForStatusCode;
import static java.lang.String.format;


/**
 * @description Terminates the request and returns the current request body as content. Useful together with the
 * template interceptor e.g. in examples.
 *
 * @topic 4. Interceptors/Features
 */
@MCElement(name="return")
public class ReturnInterceptor extends AbstractInterceptor {

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

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        exc.setResponse(new Response.ResponseBuilder().status(statusCode,getMessageForStatusCode(statusCode)).contentType(getContentType(exc)).bodyEmpty().build());
        return RETURN;
    }

    private String getContentType(Exchange exc) {
        if (contentType != null)
            return contentType;

        if (exc.getRequest().getHeader().getContentType() != null)
            return exc.getRequest().getHeader().getContentType();

        return "text/plain";
    }

    @Override
    public String getDisplayName() {
        return "Return";
    }

    @Override
    public String getShortDescription() {
        return format("Sends an response with a status code of %d and an content type of %s.",statusCode,contentType);
    }
}
