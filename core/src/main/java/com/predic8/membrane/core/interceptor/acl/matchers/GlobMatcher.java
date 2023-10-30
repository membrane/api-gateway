package com.predic8.membrane.core.interceptor.acl.matchers;

import com.predic8.membrane.core.interceptor.acl.TypeMatcher;
import com.predic8.membrane.core.util.TextUtil;

import static com.predic8.membrane.core.util.TextUtil.globToRegExp;
import static java.util.regex.Pattern.compile;

public class GlobMatcher implements TypeMatcher {
    @Override
    public boolean matches(String value, String schema) {
        return compile(globToRegExp(schema)).matcher(value).matches();
    }
}
