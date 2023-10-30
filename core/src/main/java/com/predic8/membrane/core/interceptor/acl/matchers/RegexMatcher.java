package com.predic8.membrane.core.interceptor.acl.matchers;

import com.predic8.membrane.core.interceptor.acl.TypeMatcher;

public class RegexMatcher implements TypeMatcher {
    @Override
    public boolean matches(String hostname, String ip) {
        return false;
    }
}
