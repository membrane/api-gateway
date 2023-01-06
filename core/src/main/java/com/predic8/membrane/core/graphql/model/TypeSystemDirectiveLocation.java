package com.predic8.membrane.core.graphql.model;

import java.security.InvalidParameterException;
import java.util.Objects;

public class TypeSystemDirectiveLocation extends DirectiveLocation {
    private String location;

    public TypeSystemDirectiveLocation() {
    }

    public TypeSystemDirectiveLocation(String location) {
        this.location = location;
        if (!is(location))
            throw new InvalidParameterException();
    }

    public static boolean is(String location) {
        return "SCHEMA".equals(location) ||
                "SCALAR".equals(location) ||
                "OBJECT".equals(location) ||
                "FIELD_DEFINITION".equals(location) ||
                "ARGUMENT_DEFINITION".equals(location) ||
                "INTERFACE".equals(location) ||
                "UNION".equals(location) ||
                "ENUM".equals(location) ||
                "ENUM_VALUE".equals(location) ||
                "INPUT_OBJECT".equals(location) ||
                "INPUT_FIELD_DEFINITION".equals(location);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeSystemDirectiveLocation that = (TypeSystemDirectiveLocation) o;
        return Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(location);
    }

    @Override
    public String toString() {
        return "TypeSystemDirectiveLocation{" +
                "location='" + location + '\'' +
                '}';
    }
}
