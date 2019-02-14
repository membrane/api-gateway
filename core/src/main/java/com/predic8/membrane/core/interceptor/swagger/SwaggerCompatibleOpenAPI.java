package com.predic8.membrane.core.interceptor.swagger;

import io.swagger.v3.oas.models.Paths;

import java.io.UnsupportedEncodingException;

public interface SwaggerCompatibleOpenAPI {

    String getHost();

    String getBasePath();

    Paths getPaths();

    byte[] toJSON() throws UnsupportedEncodingException;

    void setHost(String newHost);

    boolean isNull();
}
