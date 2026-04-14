/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.yaml.parsing.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static com.predic8.membrane.annot.yaml.spel.SpELContextFactory.newContext;
import static com.predic8.membrane.annot.yaml.spel.SpELEngine.eval;

public final class SpelEvaluator {

    private static final StandardEvaluationContext SPEL_CTX = newContext();
    private static final ObjectMapper SCALAR_MAPPER = new ObjectMapper();

    public Object resolve(String expression, Class<?> targetType) {
        final Object value;
        try {
            value = eval(expression, SPEL_CTX);
        } catch (RuntimeException e) {
            throw new ConfigurationParsingException("Invalid SpEL expression: %s".formatted(e.getMessage()));
        }

        if (value == null)
            return null;
        if (targetType == String.class)
            return String.valueOf(value);

        return targetType.isInstance(value) ? value : SCALAR_MAPPER.convertValue(value, targetType);
    }
}
