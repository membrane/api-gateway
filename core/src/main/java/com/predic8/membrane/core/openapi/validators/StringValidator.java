package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.swagger.v3.oas.models.media.*;
import org.slf4j.*;

import java.util.regex.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static java.lang.String.*;

public class StringValidator implements IJSONSchemaValidator {

    private static Logger log = LoggerFactory.getLogger(StringValidator.class.getName());

    private final Schema schema;

    public StringValidator(Schema schema) {
        this.schema = schema;
    }

    @Override
    public ValidationErrors validate(ValidationContext ctx, Object obj) {

        ValidationErrors errors = new ValidationErrors();

        if (obj == null) {
            errors.add(new ValidationError(ctx, "String expected but got null."));
            return errors;
        }

        String value;
        if (obj instanceof JsonNode) {
            JsonNode node = ((JsonNode) obj);
            if (!JsonNodeType.STRING.equals(node.getNodeType())) {
                errors.add(ctx, format("String expected but got %s of type %s", node, node.getNodeType()));
                return errors;
            }
            value = node.textValue();
        } else if(obj instanceof String) {
            value = (String) obj;
        } else {
            throw new RuntimeException("Should not happen! " + obj.getClass());
        }

        if (schema.getFormat() != null) {
            switch (schema.getFormat()) {
                case "uuid": {
                    if (!isValidUUID(value)) {
                        errors.add(ctx,format("The string '%s' is not a valid UUID.",value));
                    }
                    break;
                }
                case "email": {
                    if (!isValidEMail(value)) {
                        errors.add(ctx,format("The string '%s' is not a valid email.",value));
                    }
                    break;
                }
                case "uri": {
                    if (!isValidUri(value)) {
                        errors.add(ctx,format("The string '%s' is not a valid URI.",value));
                    }
                    break;
                }
                case "date": {
                    if (!isValidDate(value)) {
                        errors.add(ctx,format("The string '%s' is not a valid date of the pattern YYYY-MM-DD.",value));
                    }
                    break;
                }
                case "date-time": {
                    if (!isValidDateTime(value)) {
                        errors.add(ctx,format("The string '%s' is not a valid date-time according to ISO 8601.",value));
                    }
                    break;
                }
                case "ip": {
                    if (!isValidIp(value)) {
                        errors.add(ctx,format("The string '%s' is not a valid ip address.",value));
                    }
                }
                default:
                    log.warn("Unkown string format of {}.", schema.getFormat());
            }
        }

        if (schema.getEnum() != null) {
            if (!schema.getEnum().contains(value)) {
                errors.add(ctx,format("The string '%s' does not contain a value from the enum %s.",value,getEnumValues()));
            }
        }
        if (schema.getPattern() != null) {
            if (!matchRegexPattern(value)) {
                errors.add(ctx,format("The string '%s' does not match the regex pattern %s.",value,schema.getPattern()));
            }
        }
        return errors;
    }

    private String getEnumValues() {
        return String.join(",",schema.getEnum());
    }

    private boolean matchRegexPattern(String v) {
        return Pattern.compile(schema.getPattern()).matcher(v).matches();
    }
}
