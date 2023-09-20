/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationErrors.Direction.*;

public class ValidationErrors {

    private final static ObjectMapper om = new ObjectMapper();

    private final List<ValidationError> errors = new ArrayList<>();

    public enum Direction { REQUEST, RESPONSE }

    public static ValidationErrors create(ValidationContext ctx, String message) {
        ValidationErrors ve = new ValidationErrors();
        ve.add(ctx, message);
        return ve;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public ValidationErrors add(ValidationError error) {
        if (error != null)
            errors.add(error);
        return this;
    }

    public void add(List<ValidationError> errors) {
        this.errors.addAll(errors);
    }

    public ValidationErrors add(ValidationErrors ve) {
        if (ve != null)
            this.errors.addAll(ve.errors);
        return this;
    }

    public ValidationErrors add(ValidationContext ctx, String message) {
        errors.add(new ValidationError(ctx, message));
        return this;
    }

    public int size() {
        return errors.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public ValidationError get(int i) {
        return errors.get(i);
    }

    public Stream<ValidationError> stream() {
        return errors.stream();
    }

    public byte[] getErrorMessage(Direction direction) {

        if (errors.size() == 0)
            return "No validation errors!".getBytes();

        Map<String, List<Map<String, Object>>> m = getValidationErrorsGroupedByLocation(direction);
        Map<String, Object> wrapper = new LinkedHashMap<>();

        ValidationContext ctx = errors.get(0).getContext();
        setFieldIfNotNull(wrapper, "method", ctx.getMethod());
        setFieldIfNotNull(wrapper, "uriTemplate", ctx.getUriTemplate());
        setFieldIfNotNull(wrapper, "path", ctx.getPath());

        wrapper.put("validationErrors", m);

        try {
            return om.writerWithDefaultPrettyPrinter().writeValueAsBytes(wrapper);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "Error!".getBytes();
    }

    private Map<String, List<Map<String, Object>>> getValidationErrorsGroupedByLocation(Direction direction) {
        Map<String, List<Map<String, Object>>> m = new HashMap<>();
        errors.forEach(ve -> {
            List<Map<String, Object>> ves = new ArrayList<>();
            ves.add(ve.getContentMap());
            m.merge(getLocationFor(direction, ve), ves, (vesOld, vesNew) -> {
                vesOld.addAll(vesNew);
                return vesOld;
            });
        });
        return m;
    }

    private String getLocationFor(Direction direction, ValidationError ve) {
        if (direction.equals(REQUEST)) {
            return ve.getContext().getLocationForRequest();
        }
        return ve.getContext().getLocationForResponse();
    }

    public boolean hasErrors() {
        return size() > 0;
    }

    @Override
    public String toString() {
        return "ValidationErrors{" +
                "errors=" + errors +
                '}';
    }
}
