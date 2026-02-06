package com.predic8.membrane.annot.yaml;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.yaml.*;

public class YamlParsingException extends RuntimeException {

    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    private JsonNode node;
    private String key;
    private String nodeName;
    private String context;
    private ParsingContext<?> parsingContext;

    public YamlParsingException(String message, JsonNode node, String key, String context) {
        super(message);
        this.node = node;
        this.key = key;
        this.context = context;
    }

    public YamlParsingException(String message, JsonNode node) {
        super(message);
        this.node = node;
    }

    public YamlParsingException(Throwable cause, JsonNode node) {
        super(cause);
        this.node = node;
    }

    public JsonNode getNode() {
        return node;
    }

    public void setNode(JsonNode node) {
        this.node = node;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public ParsingContext<?> getParsingContext() {
        return parsingContext;
    }

    public void setParsingContext(ParsingContext<?> parsingContext) {
        this.parsingContext = parsingContext;
    }

    /**
     * Returns the relevant node for error display.
     * If nodeName is set and exists in the node, returns that specific child node.
     * Otherwise returns the main node.
     */
    private JsonNode getRelevantNode() {
        if (node == null) {
            return null;
        }
//        if (nodeName != null && node.get(nodeName) != null) {
//            return node.get(nodeName);
//        }
        return node;
    }

    /**
     * Converts the relevant node to YAML format.
     */
    public String getYaml() {
        var relevantNode = getRelevantNode();
        if (relevantNode == null) {
            return "";
        }
        try {
            return YAML_MAPPER.writeValueAsString(relevantNode);
        } catch (JsonProcessingException e) {
            return relevantNode.toString();
        }
    }

    /**
     * Returns a complete formatted error report including highlighted YAML.
     */
    public String getFormattedReport() {
        return YamlErrorRenderer.renderErrorReport(parsingContext.getToplevel(),parsingContext.path(), key);
    }
}