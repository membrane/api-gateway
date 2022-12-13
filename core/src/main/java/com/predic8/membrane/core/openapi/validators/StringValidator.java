/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.swagger.v3.oas.models.media.*;
import org.slf4j.*;

import java.util.regex.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static java.lang.String.*;

@SuppressWarnings("rawtypes")
public class StringValidator implements IJSONSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(StringValidator.class.getName());

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
        //noinspection unchecked
        return String.join(",",schema.getEnum());
    }

    private boolean matchRegexPattern(String v) {
        return Pattern.compile(schema.getPattern()).matcher(v).matches();
    }
}
