package com.predic8.membrane.annot.yaml;

import com.networknt.schema.Error;

import java.util.List;

public class YamlSchemaValidationException extends Exception {
    private final List<Error> errors;

    public YamlSchemaValidationException(String message, List<Error> errors) {
        super(message);
        this.errors = errors;
    }

    public List<Error> getErrors() {
        return errors;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " " + errors;
    }
}
