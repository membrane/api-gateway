package com.predic8.membrane.core.interceptor.acl;

public interface TypeMatcher {

    boolean matches(String value, String schema);
}
