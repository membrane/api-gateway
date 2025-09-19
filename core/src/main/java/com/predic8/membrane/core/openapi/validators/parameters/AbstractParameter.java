package com.predic8.membrane.core.openapi.validators.parameters;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.swagger.v3.oas.models.parameters.*;

import java.util.*;

public abstract class AbstractParameter {

    protected static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    protected List<String> values = new ArrayList<>();

    protected String type;
    protected boolean explode;

    public static AbstractParameter instance(String type, Parameter parameter) {
        AbstractParameter ap = getParameter(type);
        ap.type = type;
        ap.explode = parameter.getExplode();

        return ap;
    }

    public static AbstractParameter getParameter(String typeName) {
        AbstractParameter parameter = switch (typeName) {
            case "array" -> new ArrayParameter();
            default -> new ScalarParameter();
        };
        parameter.type = typeName;
        return parameter;
    }

    public void addAllValues(Collection<String> values) {
        this.values.addAll(values);
    }

    public abstract JsonNode getJson() throws JsonProcessingException;

    public static JsonNode asJson(String value) {
        if (value == null) return FACTORY.nullNode();
        if ("true".equals(value)) return FACTORY.booleanNode(true);
        if ("false".equals(value)) return FACTORY.booleanNode(false);
        if ("null".equals(value)) return FACTORY.nullNode();

        // integer?
        try {
            // handles +/- and no decimals
            if (!value.contains(".") && !value.contains("e") && !value.contains("E")) {
                return new java.math.BigInteger(value).bitLength() <= 31
                        ? FACTORY.numberNode(Integer.parseInt(value))
                        : FACTORY.numberNode(new java.math.BigInteger(value));
            }
        } catch (NumberFormatException ignore) { /* try decimal */ }

        // decimal?
        try {
            java.math.BigDecimal bd = new java.math.BigDecimal(value);
            // reject NaN/Infinity-equivalents (BigDecimal won’t parse them anyway)
            return FACTORY.numberNode(bd);
        } catch (NumberFormatException ignore) { /* fall through */ }

        return FACTORY.textNode(value);
    }

}
