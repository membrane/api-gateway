/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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
