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
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationErrors.Direction.*;
import static java.util.stream.Collectors.*;

public class ValidationErrors extends ArrayList<ValidationError> {

    private final static ObjectMapper om = new ObjectMapper();

    public enum Direction { REQUEST, RESPONSE }

    public static ValidationErrors create(ValidationContext ctx, String message) {
        ValidationErrors ve = new ValidationErrors();
        ve.add(ctx, message);
        return ve;
    }

    public boolean add(ValidationError error) {
        if (error != null)
            return super.add(error);
        return false;
    }

    public boolean add(ValidationErrors ve) {
        if (ve != null)
            return super.addAll(ve);
        return false;
    }

    public boolean add(ValidationContext ctx, String message) {
        return super.add(new ValidationError(ctx, message));
    }


    /**
     * Call with 400 or 500. Returns a more specifiy status code if there is any.
     */
    public int getConsolidatedStatusCode(int defaultValue) {
        return stream().map(e -> e.getContext().getStatusCode()).reduce((code, acc) -> {
            if (acc == defaultValue) return code;
            return acc;
        }).orElse(defaultValue);
    }

    public byte[] getErrorMessage(Direction direction) {

        if (isEmpty())
            return "No validation errors!".getBytes();

        Map<String, List<Map<String, Object>>> m = getValidationErrorsGroupedByLocation(direction);
        Map<String, Object> wrapper = new LinkedHashMap<>();

        ValidationContext ctx = get(0).getContext();
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
        return stream().collect(
                groupingBy(
                        e -> getLocationFor(direction, e),
                        mapping(ValidationError::getContentMap, toList())
                ));
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
                "errors=" + super.toString() +
                '}';
    }

    public static class ValidationErrorsCollector implements Collector<ValidationErrors, ValidationErrors, ValidationErrors> {

        @Override
        public Supplier<ValidationErrors> supplier() {
            return ValidationErrors::new;
        }

        @Override
        public BiConsumer<ValidationErrors, ValidationErrors> accumulator() {
            return ValidationErrors::add;
        }

        @Override
        public BinaryOperator<ValidationErrors> combiner() {
            return (a, b) -> {
                a.addAll(b);
                return a;
            };
        }

        @Override
        public Function<ValidationErrors, ValidationErrors> finisher() {
            return f -> f;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Set.of(Characteristics.IDENTITY_FINISH);
        }
    }

}
