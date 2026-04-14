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

package com.predic8.membrane.annot.yaml.parsing.binding;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.annot.yaml.ParsingContext;
import com.predic8.membrane.annot.yaml.parsing.MethodSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import static com.predic8.membrane.annot.yaml.parsing.MethodSetter.getMethodSetter;

public final class PropertyBinder {

    private static final Logger log = LoggerFactory.getLogger(PropertyBinder.class);

    public static <T> void populate(ParsingContext<?> ctx, Class<T> clazz, JsonNode node, List<Method> required, T configObj) {
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            if ("$ref".equals(key))
                continue;

            try {
                MethodSetter setter = getMethodSetter(ctx, clazz, key);
                required.remove(setter.getSetter());
                setter.setSetter(configObj, ctx, node, key);
            } catch (ConfigurationParsingException e) {
                throw e;
            } catch (Throwable cause) {
                log.debug("", cause);
                var e = new ConfigurationParsingException(cause.getMessage());
                e.setParsingContext(ctx.key(key));
                throw e;
            }
        }
    }
}
