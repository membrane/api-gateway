package com.predic8.membrane.core.openapi.validators.parameters;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;

import java.util.*;

import static java.lang.Double.*;
import static java.lang.Integer.*;
import static java.lang.Long.*;

public abstract class AbstractParameter {

    protected static final JsonNodeFactory factory = JsonNodeFactory.instance;

    protected List<String> values = new ArrayList<>();

    protected String type;
    protected boolean explode;

    public static Set<AbstractParameter> instance(Schema schema, Parameter parameter) {
        Set<String> types =  schema.getTypes();

        Set<AbstractParameter> parameters = new HashSet<>();
        types.stream().forEach(t -> {
            AbstractParameter ap = getParameter(t);
            ap.type = t;
            ap.explode = parameter.getExplode();
            parameters.add(ap);
        });

        return parameters;
    }

    public static AbstractParameter getParameter(String typeName) {
        return switch (typeName) {
            case "array" -> new ArrayParameter();
            default -> new ScalarParameter();
        };
    }

    public void addAllValues(Collection<String> values) {
        this.values.addAll(values);
    }

    public abstract JsonNode getJson() throws JsonProcessingException;

    public static JsonNode asJson(String value) {

        if (value == null)
            return factory.nullNode();

        switch (value) {
            case "true": return factory.booleanNode(true);
            case "false": return factory.booleanNode(false);
            case "null": return factory.nullNode();
            default: {
                try {
                    return factory.numberNode(parseInt(value));
                } catch (NumberFormatException ignored) {}
                try {
                    return factory.numberNode(parseLong(value));
                } catch (NumberFormatException ignored) {}
                try {
                    return factory.numberNode(parseDouble(value));
                } catch (NumberFormatException ignored) {}
                return factory.textNode(value);
            }
        }
    }
}
