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
import com.predic8.membrane.core.security.*;

import java.util.*;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

public class Request<T extends Body> extends Message<T, Request<T>> {

    private final String method;
    private String path;
    private Map<String, String> pathParameters;

    private List<SecurityScheme> securitySchemes = emptyList();

    // Security scopes from OAuth2 or API-Keys
    private Set<String> scopes;

    public Request(String method) {
        this.method = method;
    }

    public Request(String method, String path) {
        this.method = method;
        this.path = path;
    }

    public static <T extends Body> Request<T> get() {
        return new Request<>("GET");
    }

    public static <T extends Body> Request<T> get(String path) {
        return new Request<>("GET", path);
    }

    public static <T extends Body> Request<T> post() {
        return new Request<>("POST");
    }

    /**
     * Use to simplify tests
     */
    public static <T extends Body> Request<T> post(String path) {
        return new Request<>("POST", path);
    }

    public static <T extends Body> Request<T> put() {
        return new Request<>("PUT");
    }

    /**
     * Use to simplify tests
     */
    public static <T extends Body> Request<T> put(String path) {
        return new Request<>("PUT", path);
    }

    public static <T extends Body> Request<T> delete() {
        return new Request<>("DELETE");
    }

    /**
     * Use to simplify tests
     */
    public static <T extends Body> Request<T> delete(String path) {
        return new Request<>("DELETE", path);
    }

    public static <T extends Body> Request<T> patch() {
        return new Request<>("PATCH");
    }

    public static <T extends Body> Request<T> trace() {
        return new Request<>("TRACE");
    }

    public Request<T> path(String path) {
        this.path = path;
        return this;
    }

    public Request<T> securitySchemes(List<SecurityScheme> schemes) {
        this.securitySchemes = schemes;
        return this;
    }

    public Request<T> scopes(String... scope) {
        this.scopes = Arrays.stream(scope).collect(toSet());
        return this;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public void setScopes(Set<String> scopes) {
        this.scopes = scopes;
    }

    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    public List<SecurityScheme> getSecuritySchemes() {
        return securitySchemes;
    }

    public void setSecuritySchemes(List<SecurityScheme> securitySchemes) {
        this.securitySchemes = securitySchemes;
    }

    public boolean hasScheme(SecurityScheme scheme) {
        return securitySchemes.stream().anyMatch(s -> s.equals(scheme));
    }

    public void parsePathParameters(String uriTemplate) throws PathDoesNotMatchException {
        pathParameters = UriTemplateMatcher.matchTemplate(uriTemplate, path);
    }

    @Override
    public String toString() {
        return "Request{" +
               "method='" + method + '\'' +
               ", path='" + path + '\'' +
               ", pathParameters=" + pathParameters +
               ", securityScheme=" + securitySchemes +
               ", scopes=" + scopes +
               '}';
    }
}