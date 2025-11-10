package com.predic8.membrane.core.kubernetes;

import java.util.*;

public class WrongEnumConstantException extends Exception {

    private List<String> constants;
    private String value;

    public WrongEnumConstantException(Class<?> clazz, String value) {

        Object[] enumConstants = clazz.getEnumConstants();
        this.constants = Arrays.stream(enumConstants)
                .map(e -> ((Enum<?>) e).name().toLowerCase())
                .toList();
        this.value = value;
    }

    @Override
    public String getMessage() {
        return "Wrong enum constant '%s'. Possible values are: %s".formatted(value, String.join(", ", constants));
    }
}
