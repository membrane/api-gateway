package com.predic8.membrane.core.openapi.validators.parameters;

import com.fasterxml.jackson.databind.*;

import java.util.*;

public interface ParameterParser {
    
    /**
     * Build a JSON representation of the bound parameter values.
     * Implementations must be side‑effect free and thread‑safe.
     * This method is responsible to URL decode the single parameter values.
     */
    JsonNode getJson() throws ParameterParsingException;

    /**
     * Provide the raw query parameter values. Implementations must not retain
     * a mutable reference; copy defensively if needed.
     */
    void setValues(Map<String, List<String>> values);
}
