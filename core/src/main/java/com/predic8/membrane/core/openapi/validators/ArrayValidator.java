package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.util.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;

import java.util.*;

import static java.lang.String.*;


public class ArrayValidator implements IJSONSchemaValidator {

    private final Schema schema;
    private final OpenAPI api;

    public ArrayValidator(OpenAPI api, Schema schema) {
        this.api = api;
        this.schema = schema;
    }

    @Override
    public ValidationErrors validate(ValidationContext ctx, Object value) {

        ValidationErrors errors = new ValidationErrors();
        Schema itemsSchema = schema.getItems();

        JsonNode node = (JsonNode) value;

        if (itemsSchema != null) {
            for (int i = 0; i < node.size(); i++) {
                errors.add(new SchemaValidator(api, itemsSchema).validate(ctx.addJSONpointerSegment(Integer.toString(i)), node.get(i)));
            }
        }

        // @TODO Implement
        // As of 2022-11-20 parser does not support OpenAPI 3.1.0
        if (schema.getPrefixItems() != null) {
            schema.getPrefixItems().forEach(o -> {
                System.out.println("o = " + o);
            });
        }

        if (schema.getMinItems() != null && node.size() < schema.getMinItems()) {
            errors.add(ctx, format("Array has %d items. This is less then minItems of %d.", node.size(), schema.getMinItems()));
        }

        if (schema.getMaxItems() != null && node.size() > schema.getMaxItems()) {
            errors.add(ctx, format("Array has %d items. This is more then maxItems of %d.", node.size(), schema.getMaxItems()));
        }

        errors.add(validateUniqueItems(ctx, node));

        return errors;
    }

    private ValidationErrors validateUniqueItems(ValidationContext ctx, JsonNode node) {
        if (schema.getUniqueItems() == null || !schema.getUniqueItems())
            return null;

        Set<JsonNode> itemValues = new HashSet<>();
        List<String> moreThanOnce = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            if (itemValues.contains(node.get(i))) {
                moreThanOnce.add(node.get(i).toString());
            }
            itemValues.add(node.get(i));
        }
        if (moreThanOnce.size() > 0) {
            return ValidationErrors.create(ctx, format("Array with restriction uniqueItems has the not unique values %s.", Utils.joinByComma(moreThanOnce)));
        }

        return null;
    }
}
