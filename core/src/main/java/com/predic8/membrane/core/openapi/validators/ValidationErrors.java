package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.*;

import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.openapi.util.Utils.setFieldIfNotNull;
import static java.util.stream.Collectors.toList;

public class ValidationErrors {

    private final static ObjectMapper om = new ObjectMapper();

    private final List<ValidationError> errors = new ArrayList<>();

    public static ValidationErrors create(ValidationContext ctx, String message) {
        ValidationErrors ve = new ValidationErrors();
        ve.add(ctx,message);
        return ve;
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
        errors.add(new ValidationError(ctx,message));
        return this;
    }

    public int size() {
        return errors.size();
    }

    public ValidationError get(int i) {
        return errors.get(i);
    }

    public Stream<ValidationError> stream() {
        return errors.stream();
    }

    /**
     * Call with 400 or 500. Returns a more specifiy status code if there is any.
     */
    public int getConsolidatedStatusCode(int defaultValue) {
        return errors.stream().map(e -> e.getContext().getStatusCode()).reduce((code, acc) -> {
            if (acc == defaultValue) return code;
            return acc;
        }).orElse(defaultValue);
    }

    @Override
    public String toString() {

        if (errors.size() == 0)
            return "No validation errors!";

        Map<String, List<Map<String,Object>>> m = getValidationErrorsGroupedByLocation();
        Map<String,Object> wrapper = new LinkedHashMap<>();

        ValidationContext ctx = errors.get(0).getContext();
        setFieldIfNotNull(wrapper,"method",ctx.getMethod());
        setFieldIfNotNull(wrapper,"uriTemplate",ctx.getUriTemplate());
        setFieldIfNotNull(wrapper,"path",ctx.getPath());

        wrapper.put("validationErrors", m);

        try {
            return om.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "Error!";
    }

    private Map<String, List<Map<String,Object>>> getValidationErrorsGroupedByLocation() {
        Map<String,List<Map<String,Object>>> m = new HashMap<>();
        errors.forEach(ve -> {
            List<Map<String,Object>> ves = new ArrayList<>();
            ves.add(ve.getContentMap());
            m.merge(ve.getContext().getLocationForRequest(), ves, (vesOld, vesNew) -> {
                vesOld.addAll(vesNew);
                return vesOld;
            });
        });
        return m;
    }
}