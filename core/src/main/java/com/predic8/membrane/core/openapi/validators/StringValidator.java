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
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static com.predic8.membrane.core.openapi.validators.ValidationError.v;
import static java.lang.String.*;

@SuppressWarnings("rawtypes")
public class StringValidator implements JsonSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(StringValidator.class.getName());

    private final Schema schema;

    public StringValidator(Schema schema) {
        this.schema = schema;
    }

    @Override
    public String canValidate(Object obj) {
        if (obj instanceof JsonNode node && JsonNodeType.STRING.equals(node.getNodeType())) {
            return STRING;
        }
        if (obj instanceof String) {
            return STRING;
        }
        return null;
    }

    @Override
    public ValidationErrors validate(ValidationContext ctx, Object obj) {
        ctx = ctx.schemaType("string");

        ValidationErrors errors = new ValidationErrors();

        if (obj == null) {
            errors.add(new ValidationError(ctx, "String expected but got null."));
            return errors;
        }

        String value;
        if (obj instanceof JsonNode node) {
            if (JsonNodeType.STRING.equals(node.getNodeType())) {
                value = node.textValue();
            } else if (QUERY_PARAMETER.equals(ctx.getValidatedEntityType())) {
                value = node.asText();
            } else {
                errors.add(ctx, mask -> format("String expected but got %s of type %s", v(node, mask), node.getNodeType()));
                return errors;
            }
        } else if (obj instanceof String s) {
            value = s;
        } else {
            throw new RuntimeException("Should not happen! " + obj.getClass());
        }

        if (schema.getFormat() != null) {
            errors.add(validateFormat(ctx, value));
        }

        errors.add(new StringRestrictionValidator(schema).validate(ctx, value));

        return errors;
    }

    private ValidationErrors validateFormat(ValidationContext ctx, String value) {
        switch (schema.getFormat()) {
            case "uuid": {
                if (!isValidUUID(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid UUID.", v(value, mask)));
                break;
            }
            case "email": {
                if (!isValidEMail(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid E-Mail.", v(value, mask)));
                break;
            }
            case "uri": {
                if (!isValidUri(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid URI.", v(value, mask)));
                break;
            }
            case "date": {
                if (!isValidDate(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid date of the pattern YYYY-MM-DD.", v(value, mask)));
                break;
            }
            case "date-time": {
                if (!isValidDateTime(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid date-time according to ISO 8601.", v(value, mask)));
                break;
            }
            case "duration": {
                if (!isValidDuration(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid duration.", v(value, mask)));
                break;
            }
            case "ip", "ipv4": {
                if (!isValidIp(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid IPv4 address.", v(value, mask)));
                break;
            }
            case "ipv6": {
                if (!isValidIpV6(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid IPv6 address.", v(value, mask)));
                break;
            }
            case "idn-email": {
                if (!isValidEMail(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid E-Mail address.", v(value, mask)));
                break;
            }
            case "uri-reference": {
                if (!isValidUri(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid URI reference.", v(value, mask)));
                break;
            }
            case "iri": {
                if (!isValidUri(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid IRI.", v(value, mask)));
                break;
            }
            case "iri-reference": {
                if (!isValidUri(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid IRI reference.", v(value, mask)));
                break;
            }
            case "hostname", "idn-hostname": {
                if (!isValidHostname(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid hostname.", v(value, mask)));
                break;
            }
            case "json-pointer": {
                if (!isValidJsonPointer(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid JSON pointer.", v(value, mask)));
                break;
            }
            case "relative-json-pointer": {
                if (!isValidRelativeJsonPointer(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid relative JSON pointer.", v(value, mask)));
                break;
            }
            case "gtin-13": {
                if (!isValidGlobalTradeItemNumber(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid GTIN-13 number.", v(value, mask)));
                break;
            }
            case "iso-3166-alpha-2": {
                if (!isValidIso3166Alpha2(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid ISO-3166-1-alpha-2 number.", v(value, mask)));
                break;
            }
            case "iso-4217": {
                if (!isValidIso4217(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid currency code according to ISO 4217.", v(value, mask)));
                break;
            }
            case "bcp47": {
                if (!isValidBCP47(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid multi letter language tag according to BCP47.", v(value, mask)));
                break;
            }
            case "iso-639": {
                if (!isValidIso639(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid language code according to ISO 639.", v(value, mask)));
                break;
            }
            case "iso-639-1": {
                if (!isValidIso639_1(value))
                    return ValidationErrors.error(ctx, mask -> format("The string '%s' is not a valid two letter language code according to ISO 639-1.", v(value, mask)));
                break;
            }
            default:
                log.warn("Unknown string format of {}.", schema.getFormat());
        }
        return null;
    }
}
