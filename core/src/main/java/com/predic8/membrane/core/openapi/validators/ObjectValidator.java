package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.openapi.util.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.util.Utils.joinByComma;
import static java.lang.String.*;

/**
 * Not supported:
 * <p>
 * - propertyNames from JSON Schema draft 6
 * - is it part of OpenAPI?
 */

public class ObjectValidator implements IJSONSchemaValidator {

    private Schema schema;
    final private OpenAPI api;

    /**
     * ObjectMapper is Thread safe!
     * https://fasterxml.github.io/jackson-databind/javadoc/2.6/com/fasterxml/jackson/databind/ObjectMapper.html
     */
    final private ObjectMapper om = new ObjectMapper();

    public ObjectValidator(OpenAPI api, Schema schema) {
        this.api = api;
        this.schema = schema;
        if (schema.get$ref() != null) {
            this.schema = getSchemaFromRef();
        }
    }

    @Override
    public ValidationErrors validate(ValidationContext ctx, Object value) {
        try {
            return validate(ctx, om.readValue((InputStream) value, JsonNode.class));
        } catch (IOException e) {
            ValidationErrors errors = new ValidationErrors();
            errors.add(new ValidationError(ctx.statusCode(400), "Request body cannot be parsed as JSON"));
            return errors;
        }
    }

    public ValidationErrors validate(ValidationContext ctx, ObjectNode node) {
        return validate(ctx, (JsonNode) node);
    }

    public ValidationErrors validate(ValidationContext ctx, JsonNode node) {
        ValidationErrors errors = validateRequiredProperties(ctx, node);
        errors.add(validateAddionalProperties(ctx, node));
        errors.add(validateProperties(ctx, node));
        errors.add(validateSize(ctx, node));
        errors.add(validateDiscriminator(ctx,node));
        return errors;
    }

    /**
     * @TODO implement Discriminator/mapping
     *
     * @param ctx
     * @param node
     * @return
     */
    private ValidationErrors validateDiscriminator(ValidationContext ctx, JsonNode node) {
        if (schema.getDiscriminator() == null)
            return null;

        String propertyName = schema.getDiscriminator().getPropertyName();

        System.out.println("schema.getType() = " + schema.getType());
        System.out.println("ctx.getComplexType() = " + ctx.getComplexType());



        if (schema.getType().equals(propertyName))
            return null;

        String baseSchemaName = node.get(propertyName).asText();

        Schema baseSchema = api.getComponents().getSchemas().get(baseSchemaName);
        return new SchemaValidator(api,baseSchema).validate(ctx,node);
    }

    private ValidationErrors validateSize(ValidationContext ctx, JsonNode node) {
        ValidationErrors errors = new ValidationErrors();
        errors.add(validateMinProperties(ctx, node));
        errors.add(validateMaxProperties(ctx, node));
        return errors;
    }

    private ValidationErrors validateMinProperties(ValidationContext ctx, JsonNode node) {
        if (schema.getMinProperties() == null)
            return null;

        if (node.size() < schema.getMinProperties().intValue()) {
            return ValidationErrors.create(ctx, String.format("Object has %d properties. This is smaller then minProperties of %d.", node.size(), schema.getMinProperties()));
        }
        return null;
    }

    private ValidationErrors validateMaxProperties(ValidationContext ctx, JsonNode node) {
        if (schema.getMaxProperties() == null)
            return null;

        if (node.size() > schema.getMaxProperties().intValue()) {
            return ValidationErrors.create(ctx, String.format("Object has %d properties. This is more then maxProperties of %d.", node.size(), schema.getMaxProperties()));
        }
        return null;
    }

    private ValidationErrors validateAddionalProperties(ValidationContext ctx, JsonNode node) {
        if (schema.getAdditionalProperties() == null)
            return null;

        Map<String, JsonNode> addionalProperties = getAddionalProperties(node);

        if (addionalProperties.size() == 0)
            return null;

        ValidationErrors errors = new ValidationErrors();

        if (schema.getAdditionalProperties() instanceof Schema) {
            addionalProperties.forEach((propName, value) -> {
                errors.add(new SchemaValidator(api, (Schema) schema.getAdditionalProperties()).validate(ctx.addJSONpointerSegment(propName), value));
            });
            return errors;
        }

        return errors.add(ctx.statusCode(400), format("The object has the additional %s: %s .But the schema does not allow additional properties.", getPropertyOrIes(addionalProperties.keySet()), joinByComma(addionalProperties.keySet())));
    }

    private String getPropertyOrIes(Set<String> addionalProperties) {
        String propWord = "Property";
        if (addionalProperties.size() > 1) {
            propWord = "Properties";
        }
        return propWord;
    }

    private Map<String, JsonNode> getAddionalProperties(JsonNode node) {
        Map<String, JsonNode> addionalProperties = new HashMap<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String propName = it.next();
            if (!schema.getProperties().containsKey(propName)) {
                addionalProperties.put(propName, node.get(propName));
            }
        }
        return addionalProperties;
    }

    private ValidationErrors validateRequiredProperties(ValidationContext ctx, JsonNode node) {

        List<String> required = schema.getRequired();
        if (required == null)
            return new ValidationErrors();

        return createErrorsForMissingRequiredProperties(ctx, getMissingProperties(ctx, node, required));
    }

    private List<String> getMissingProperties(ValidationContext ctx, JsonNode node, List<String> required) {
        List<String> missingProperties = new ArrayList<>();
        required.forEach(requiredProp -> {
            if (node.get(requiredProp) == null) {
                if (ctx.getValidatedEntity().equals("REQUEST")) {
                    if (isPropertyReadOnly(requiredProp)) {
                        return;
                    }
                } else {
                    if (isPropertyWriteOnly(requiredProp)) {
                        return;
                    }
                }
                missingProperties.add(requiredProp);
            }
        });
        return missingProperties;
    }

    private boolean isPropertyReadOnly(String propertyName) {
        Schema propSchema = (Schema) schema.getProperties().get(propertyName);
        return propSchema.getReadOnly() != null && propSchema.getReadOnly();
    }

    private boolean isPropertyWriteOnly(String propertyName) {
        Schema propSchema = (Schema) schema.getProperties().get(propertyName);
        return propSchema.getWriteOnly() != null && propSchema.getWriteOnly();
    }

    private ValidationErrors createErrorsForMissingRequiredProperties(ValidationContext ctx, List<String> missingProperties) {
        ValidationErrors errors = new ValidationErrors();

        if (missingProperties.size() == 1) {
            errors.add(new ValidationError(ctx.addJSONpointerSegment(missingProperties.get(0)), format("Required property %s is missing.", missingProperties.get(0))));
        } else if (missingProperties.size() > 1) {
            String missing = String.join(",", missingProperties);
            errors.add(new ValidationError(ctx, format("Required properties %s are missing in object %s.", missing, ctx.getJSONpointer())));
        }
        return errors;
    }

    private ValidationErrors validateProperties(ValidationContext ctx, JsonNode node) {
        ValidationErrors errors = new ValidationErrors();

        if (schema.getProperties() == null)
            return errors;

        Map<String, Schema> schemaProperties = schema.getProperties();

        schemaProperties.forEach((propertyName, propertySchema) -> {

            errors.add(validateProperty(propertyName, propertySchema, node, ctx.addJSONpointerSegment(propertyName)));
            errors.add(validateReadOnlyProperty(ctx, node, propertyName, propertySchema));
            errors.add(validateWriteOnlyProperty(ctx, node, propertyName, propertySchema));
        });
        return errors;
    }

    private ValidationErrors validateReadOnlyProperty(ValidationContext ctx, JsonNode node, String propertyName, Schema propertySchema) {
        if (propertySchema.getReadOnly() == null || !propertySchema.getReadOnly())
            return null;

        if (!ctx.getValidatedEntity().equals("REQUEST"))
            return null;

        if (node.get(propertyName) == null)
            return null;

        return ValidationErrors.create(ctx.addJSONpointerSegment(propertyName), String.format("The property %s is read only. But the request contains the value %s for this field.", propertyName, node.get(propertyName)));
    }

    private ValidationErrors validateWriteOnlyProperty(ValidationContext ctx, JsonNode node, String
            propertyName, Schema propertySchema) {
        if (propertySchema.getWriteOnly() == null || !propertySchema.getWriteOnly())
            return null;

        if (!ctx.getValidatedEntity().equals("RESPONSE"))
            return null;

        if (node.get(propertyName) == null)
            return null;

        return ValidationErrors.create(ctx.addJSONpointerSegment(propertyName), String.format("The property %s is write only. But the response contained the value %s.", propertyName, node.get(propertyName)));
    }

    private Schema getSchemaFromRef() {

        // could be removed later. Only to debug.
        if (schema.get$ref() == null)
            return null;

        ObjectHolder<Schema> oh = new ObjectHolder();
        api.getComponents().getSchemas().forEach((schemaName, refSchema) -> {
            if (schemaName.equals(getSchemaNameFromRef())) {
                oh.setValue(refSchema);
            }

        });
        return oh.getValue();
    }

    private String getSchemaNameFromRef() {
        return Utils.getComponentLocalNameFromRef(schema.get$ref());
    }

    private ValidationErrors validateProperty(String propertyName, Schema schema, JsonNode node, ValidationContext
            ctx) {
        ValidationErrors errors = new ValidationErrors();
        if (node.get(propertyName) != null)
            errors.add(new SchemaValidator(api, schema).validate(ctx, node.get(propertyName)));
        return errors;
    }
}
