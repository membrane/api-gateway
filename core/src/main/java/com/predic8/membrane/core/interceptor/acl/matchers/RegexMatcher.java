package com.predic8.membrane.core.interceptor.acl.matchers;

import com.predic8.membrane.core.interceptor.acl.TypeMatcher;

import static java.util.regex.Pattern.compile;


public class RegexMatcher implements TypeMatcher {
    @Override
    public boolean matches(String value, String schema) {
        return compile(schema).matcher(value).matches();
    }
}
