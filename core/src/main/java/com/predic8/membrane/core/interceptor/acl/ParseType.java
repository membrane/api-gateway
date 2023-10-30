package com.predic8.membrane.core.interceptor.acl;

import java.util.Arrays;

public enum ParseType {
    GLOB("glob"),
    REGEX("regex"),
    CIDR("cidr");

    private String value;

    ParseType(String value) {
        this.value = value;
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
}
