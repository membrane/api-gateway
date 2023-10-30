package com.predic8.membrane.core.interceptor.acl;

import com.predic8.membrane.core.interceptor.acl.matchers.CidrMatcher;
import com.predic8.membrane.core.interceptor.acl.matchers.GlobMatcher;
import com.predic8.membrane.core.interceptor.acl.matchers.RegexMatcher;

import java.util.Arrays;

public enum ParseType {
    GLOB("glob", new GlobMatcher()),
    REGEX("regex", new RegexMatcher()),
    CIDR("cidr", new CidrMatcher());

    private String value;
    private TypeMatcher matcher;

    ParseType(String value, TypeMatcher matcher) {
        this.value = value;
        this.matcher = matcher;
    }

    public static ParseType getByOrDefault(String string) {
        return Arrays.stream(values())
                .filter(v -> v.value.equals(string.toLowerCase()))
                .findFirst()
                .orElse(ParseType.GLOB);
    }

    @Override
    public String toString() {
        return value;
    }

    public TypeMatcher getMatcher() {
        return matcher;
    }
}