package com.predic8.membrane.core.openapi.validators.parameters;

public class AdditionalPropertiesException extends Exception {

    private String property;

    public AdditionalPropertiesException(String property) {
        this.property = property;
    }

    public String getProperty() {
        return property;
    }
}
