package com.predic8.membrane.core.interceptor.acl;

import com.predic8.membrane.core.interceptor.acl.matchers.*;
import com.predic8.membrane.core.interceptor.acl.matchers.Cidr.CidrMatcher;

public enum ParseType {
    GLOB("glob", new GlobMatcher()),
    REGEX("regex", new RegexMatcher()),
    CIDR("cidr", new CidrMatcher());

    private final String value;
    private final TypeMatcher matcher;

    ParseType(String value, TypeMatcher matcher) {
        this.value = value;
        this.matcher = matcher;
    }

    public static ParseType getByOrDefault(String string) {
        if (string != null) {
            for (ParseType type : values()) {
                if (string.equalsIgnoreCase(type.value)) {
                    return type;
                }
            }
        }
        return GLOB;
    }

    @Override
    public String toString() {
        return value;
    }

    public TypeMatcher getMatcher() {
        return matcher;
    }
}
