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

import java.util.*;

import static com.predic8.membrane.core.openapi.util.Utils.setFieldIfNotNull;

public class ValidationError {

    /**
     * Replacement token for a submitted (potentially sensitive) value when a message is rendered
     * with masking enabled.
     */
    public static final String MASK = "***";

    private final MaskableMessage message;
    private final ValidationContext ctx;

    public ValidationError(String message) {
        this(null, message);
    }

    public ValidationError(ValidationContext ctx, String message) {
        this(ctx, maskValues -> message);
    }

    public ValidationError(ValidationContext ctx, MaskableMessage message) {
        this.message = message;
        this.ctx = ctx;
    }

    /**
     * Renders a submitted value for inclusion in a validation message: masked ({@link #MASK}) when
     * {@code maskValues} is set, otherwise shown in clear. Kept out of the message string until
     * render time so response body and log can be masked independently.
     */
    public static String v(Object value, boolean maskValues) {
        return maskValues ? MASK : String.valueOf(value);
    }

    /**
     * The message with submitted values shown in clear.
     */
    public String getMessage() {
        return message.render(false);
    }

    /**
     * The message with submitted values either masked ({@link #MASK}) or shown in clear.
     */
    public String getMessage(boolean maskValues) {
        return message.render(maskValues);
    }

    public ValidationContext getContext() {
        return ctx;
    }

    public Map<String,Object> getContentMap() {
        return getContentMap(false);
    }

    /**
     * Takes out all fields with null values
     */
    @SuppressWarnings("ConstantConditions")
    public Map<String,Object> getContentMap(boolean maskValues) {
        Map<String,Object> fields = new LinkedHashMap<>();
        setFieldIfNotNull(fields,"message", getMessage(maskValues));
        setFieldIfNotNull(fields,"complexType",ctx.getComplexType());
        setFieldIfNotNull(fields,"schemaType",ctx.getSchemaType());

        return fields;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean maskValues) {
        if (ctx == null)
            return getMessage(maskValues);
        return String.format("%s %s %s: %s",ctx.getMethod(), ctx.getPath(), ctx.getJSONpointer(), getMessage(maskValues));
    }
}
