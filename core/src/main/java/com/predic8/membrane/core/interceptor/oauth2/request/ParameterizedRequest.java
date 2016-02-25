/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2.request;

import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.ReusableJsonGenerator;
import com.predic8.membrane.core.util.URLParamUtil;

import java.io.IOException;
import java.util.Map;

public abstract class ParameterizedRequest {
    protected Exchange exc;
    protected OAuth2AuthorizationServerInterceptor authServer;
    protected Map<String,String> params;
    protected ReusableJsonGenerator jsonGen;
    protected Response requestResponse;

    protected abstract void checkForMissingParameters() throws IOException;
    protected abstract void validateParameters();
    protected abstract Response getResponse();

    public Response validateRequest() throws IOException {
        checkForMissingParameters();
        validateParameters();
        return getResponse();
    }


    public ParameterizedRequest(OAuth2AuthorizationServerInterceptor authServer, Exchange exc) throws Exception {
        this.authServer = authServer;
        this.exc = exc;
        this.params = getValidParams(exc);
        this.jsonGen = new ReusableJsonGenerator();
    }

    private Map<String, String> getValidParams(Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(authServer.getRouter().getUriFactory(), exc);
        removeEmptyParams(params);
        return params;
    }

    protected void removeEmptyParams(Map<String, String> params) {
        for (String paramName : params.keySet()) {
            if (params.get(paramName).isEmpty())
                params.remove(paramName);
        }
    }

    public Response createParameterizedJsonErrorResponse(Exchange exc, String... params) throws IOException {
        if (params.length % 2 != 0)
            throw new IllegalArgumentException("The number of strings passed as params is not even");
        String json;
        synchronized (jsonGen) {
            JsonGenerator gen = jsonGen.resetAndGet();
            gen.writeStartObject();
            for (int i = 0; i < params.length; i += 2)
                gen.writeObjectField(params[i], params[i + 1]);
            gen.writeEndObject();
            json = jsonGen.getJson();
        }

        return Response.badRequest()
                .body(json)
                .contentType(MimeType.APPLICATION_JSON_UTF8)
                .dontCache()
                .build();
    }
}
