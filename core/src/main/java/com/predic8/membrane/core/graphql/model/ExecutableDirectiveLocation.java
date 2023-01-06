package com.predic8.membrane.core.graphql.model;

import java.security.InvalidParameterException;
import java.util.Objects;

public class ExecutableDirectiveLocation extends DirectiveLocation {
    private String location;

    public ExecutableDirectiveLocation() {
    }

    public ExecutableDirectiveLocation(String location) {
        this.location = location;
        if (!is(location))
            throw new InvalidParameterException();
    }

    public static boolean is(String location) {
        return "QUERY".equals(location) || "MUTATION".equals(location) || "SUBSCRIPTION".equals(location) ||
                "FIELD".equals(location) || "FRAGMENT_DEFINITION".equals(location) ||
                "FRAGMENT_SPREAD".equals(location) || "INLINE_FRAGMENT".equals(location) ||
                "VARIABLE_DEFINITION".equals(location);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutableDirectiveLocation that = (ExecutableDirectiveLocation) o;
        return Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(location);
    }

    @Override
    public String toString() {
        return "ExecutableDirectiveLocation{" +
                "location='" + location + '\'' +
                '}';
    }
}
