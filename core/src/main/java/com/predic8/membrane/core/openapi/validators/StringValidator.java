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
        } else if(obj instanceof String) {
            return STRING;
        } else {
            return null;
        }
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
            if (!JsonNodeType.STRING.equals(node.getNodeType())) {
                errors.add(ctx, format("String expected but got %s of type %s", node, node.getNodeType()));
                return errors;
            }
            value = node.textValue();
        } else if(obj instanceof String s) {
            value = s;
        } else {
            throw new RuntimeException("Should not happen! " + obj.getClass());
        }

        if (schema.getFormat() != null) {
            switch (schema.getFormat()) {
                case "uuid": {
                    if (!isValidUUID(value))
                        errors.add(ctx, format("The string '%s' is not a valid UUID.", value));
                    break;
                }
                case "email": {
                    if (!isValidEMail(value))
                        errors.add(ctx, format("The string '%s' is not a valid E-Mail.", value));
                    break;
                }
                case "uri": {
                    if (!isValidUri(value))
                        errors.add(ctx, format("The string '%s' is not a valid URI.", value));
                    break;
                }
                case "date": {
                    if (!isValidDate(value))
                        errors.add(ctx,format("The string '%s' is not a valid date of the pattern YYYY-MM-DD.", value));
                    break;
                }
                case "date-time": {
                    if (!isValidDateTime(value))
                        errors.add(ctx, format("The string '%s' is not a valid date-time according to ISO 8601.", value));
                    break;
                }
                case "duration": {
                    if (!isValidDuration(value))
                        errors.add(ctx, format("The string '%s' is not a valid duration.", value));
                    break;
                }
                case "ip", "ipv4": {
                    if (!isValidIp(value))
                        errors.add(ctx, format("The string '%s' is not a valid IPv4 address.", value));
                    break;
                }
                case "ipv6": {
                    if (!isValidIpV6(value))
                        errors.add(ctx, format("The string '%s' is not a valid IPv6 address.", value));
                    break;
                }
                case "idn-email": {
                    if (!isValidEMail(value))
                        errors.add(ctx, format("The string '%s' is not a valid E-Mail address.", value));
                    break;
                }
                case "uri-reference": {
                    if (!isValidUri(value))
                        errors.add(ctx, format("The string '%s' is not a valid URI reference.", value));
                    break;
                }
                case "iri": {
                    if (!isValidUri(value))
                        errors.add(ctx, format("The string '%s' is not a valid IRI.", value));
                    break;
                }
                case "iri-reference": {
                    if (!isValidUri(value))
                        errors.add(ctx, format("The string '%s' is not a valid IRI reference.", value));
                    break;
                }
                case "hostname", "idn-hostname": {
                    if (!isValidHostname(value))
                        errors.add(ctx, format("The string '%s' is not a valid hostname.", value));
                    break;
                }
                case "json-pointer": {
                    if (!isValidJsonPointer(value))
                        errors.add(ctx, format("The string '%s' is not a valid JSON pointer.", value));
                    break;
                }
                case "relative-json-pointer": {
                    if (!isValidRelativeJsonPointer(value))
                        errors.add(ctx, format("The string '%s' is not a valid relative JSON pointer.", value));
                    break;
                }
                case "gtin-13": {
                    if (!isValidGlobalTradeItemNumber(value))
                        errors.add(ctx, format("The string '%s' is not a valid GTIN-13 number.", value));
                    break;
                }
                case "iso-3166-alpha-2": {
                    if (!isValidIso3166Alpha2(value))
                        errors.add(ctx, format("The string '%s' is not a valid ISO-3166-1-alpha-2 number.", value));
                    break;
                }
                case "iso-4217": {
                    if (!isValidIso4217(value))
                        errors.add(ctx, format("The string '%s' is not a valid currency code according to ISO 4217.", value));
                    break;
                }
                case "bcp47": {
                    if (!isValidBCP47(value))
                        errors.add(ctx, format("The string '%s' is not a valid multi letter language tag according to BCP47.", value));
                    break;
                }
                case "iso-639": {
                    if (!isValidIso639(value))
                        errors.add(ctx, format("The string '%s' is not a valid language code according to ISO 639.", value));
                    break;
                }
                case "iso-639-1": {
                    if (!isValidIso639_1(value))
                        errors.add(ctx, format("The string '%s' is not a valid two letter language code according to ISO 639-1.", value));
                    break;
                }
                default:
                    log.warn("Unknown string format of {}.", schema.getFormat());
            }
        }

        if (schema.getConst() != null && !schema.getConst().equals(value)) {
            errors.add(ctx,format("The string '%s' does not match the const %s.", value, schema.getConst()));
        }
        else if (schema.getEnum() != null && !schema.getEnum().contains(value)) {
            errors.add(ctx,format("The string '%s' does not contain a value from the enum %s.",value,getEnumValues()));
        }

        if (schema.getPattern() != null && !matchRegexPattern(value)) {
                errors.add(ctx,format("The string '%s' does not match the regex pattern %s.",value,schema.getPattern()));
        }

        return errors;
    }

    private String getEnumValues() {
        //noinspection unchecked
        return String.join(",",schema.getEnum());
    }

    private boolean matchRegexPattern(String v) {
        return Pattern.compile(schema.getPattern()).matcher(v).find();
    }
}
