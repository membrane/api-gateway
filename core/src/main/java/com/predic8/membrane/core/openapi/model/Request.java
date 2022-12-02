package com.predic8.membrane.core.openapi.model;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.util.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.util.PathUtils.*;

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

    /**
     * Method is not in base class so the "builder" still works
     */
//    public Request body(String s) {
//        this.body = new StringBody(s);
//        return this;
//    }
//
//    public Request body(JsonNode n) {
//        this.body = new JsonBody(n);
//        this.mediaType("application/json");
//        return this;
//    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String,String> getQueryParams() {
        return PathUtils.parseQueryString(path);
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

    public void ajustPathAccordingToBasePath(String basePath) {
        path = PathUtils.ajustPathAccordingToBasePath(basePath,path);
    }

//    public void ajustPathAccordingToBasePath(String basePath) {
//        if (basePath.length() == 0)
//            return;
//
//        path =  normalizeUri(path.replace(basePath, "/"));
//    }


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
