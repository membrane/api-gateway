package com.predic8.membrane.annot.yaml;

import com.fasterxml.jackson.core.*;

public class ConfigurationParsingException extends RuntimeException {

    private ParsingContext<?> parsingContext;

    public ConfigurationParsingException(String message) {
        super(message);
    }

    public ConfigurationParsingException(Throwable cause) {
        super(cause);
    }

    public ParsingContext<?> getParsingContext() {
        return parsingContext;
    }

    public void setParsingContext(ParsingContext<?> parsingContext) {
        this.parsingContext = parsingContext;
    }

    /**
     * Returns a complete formatted error report including highlighted YAML.
     */
    public String getFormattedReport() throws JsonProcessingException {
        return YamlErrorRenderer.renderErrorReport(parsingContext);
    }
}