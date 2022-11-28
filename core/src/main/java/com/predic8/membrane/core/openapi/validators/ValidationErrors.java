package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.*;

import java.util.*;
import java.util.stream.*;

public class ValidationErrors {

    private final static ObjectMapper om = new ObjectMapper();

    private List<ValidationError> errors = new ArrayList<>();

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

    @Override
    public String toString() {

        Map wrapper = new HashMap();
        wrapper.put("validationErrors", errors);

        try {
            return om.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "Error!";
    }
}
