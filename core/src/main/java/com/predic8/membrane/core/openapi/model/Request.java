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

package com.predic8.membrane.core.openapi.model;

import com.predic8.membrane.core.openapi.util.*;

import java.util.*;

public class Request extends Message<Request> {

    private String method;
    private String path;
    private UriTemplateMatcher uriTemplateMatcher = new UriTemplateMatcher();
    private Map<String,String> pathParameters;

    public Request(String method) {
        this.method = method;
    }

    public static Request get() {
        return new Request("GET");
    }

    public static Request post() {
        return new Request("POST");
    }

    public static Request put() {
        return new Request("PUT");
    }

    public static Request delete() {
        return new Request("DELETE");
    }

    public static Request patch() {
        return new Request("PATCH");
    }

    public static Request trace() {
        return new Request("TRACE");
    }

    public Request path(String path) {
        this.path = path;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    // TODO
    public Map<String,String> getQueryParams() {
        return UriUtil.parseQueryString(path);
    }

    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    public Map<String, String> parsePathParameters(String uriTemplate) throws PathDoesNotMatchException {
        if (pathParameters == null) {
            pathParameters = uriTemplateMatcher.match(uriTemplate, path);
        }
        return pathParameters;
    }

    @Override
    public String toString() {
        return "Request{" +
                "method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", uriTemplateMatcher=" + uriTemplateMatcher +
                ", pathParameters=" + pathParameters +
                '}';
    }

}
