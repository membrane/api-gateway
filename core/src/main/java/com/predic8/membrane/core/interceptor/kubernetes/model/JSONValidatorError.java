package com.predic8.membrane.core.interceptor.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JSONValidatorError {

    private List<String> errors;

    public JSONValidatorError() {}

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
