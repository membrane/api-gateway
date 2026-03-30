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

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.annot.yaml.ParsingContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import static com.predic8.membrane.annot.yaml.McYamlIntrospector.getAnySetter;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.getChildSetter;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.findSetterForKey;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.hasAttributes;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.hasChildren;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.hasOtherAttributes;

public final class SetterResolver {

    private static final Logger log = LoggerFactory.getLogger(SetterResolver.class);

    public <T> @NotNull ResolvedSetter resolve(ParsingContext<?> ctx, Class<T> clazz, String key) {
        Method setter = findSetterForKey(clazz, key);
        if (setter.getAnnotation(MCChildElement.class) != null) {
            if (!java.util.List.class.isAssignableFrom(setter.getParameterTypes()[0]))
                setter = null;
        }

        Class<?> beanClass = null;
        if (setter == null) {
            if (hasOtherAttributes(clazz) && !hasAttributes(clazz) && !hasChildren(clazz)) {
                return new ResolvedSetter(getAnySetter(clazz), null);
            }

            try {
                beanClass = ctx.findClass(key);
                if (beanClass != null)
                    setter = getChildSetter(clazz, beanClass);
            } catch (Exception e) {
                throwCantFindException(ctx, clazz, key);
            }

            if (setter == null)
                setter = getAnySetter(clazz);
        }
        return new ResolvedSetter(setter, beanClass);
    }

    private static <T> void throwCantFindException(ParsingContext<?> ctx, Class<T> clazz, String key) {
        log.debug("Can't find method or bean for key '{}' in {}", key, getConfigElementName(clazz));
        var e = new ConfigurationParsingException("Invalid field '%s' in %s".formatted(key, getConfigElementName(clazz)));
        e.setParsingContext(ctx.key(key));
        throw e;
    }

    private static String getConfigElementName(Class<?> clazz) {
        MCChildElement childAnnotation = clazz.getAnnotation(MCChildElement.class);
        if (childAnnotation != null)
            return childAnnotation.toString();

        MCElement mcAnnotation = clazz.getAnnotation(MCElement.class);
        if (mcAnnotation != null)
            return mcAnnotation.name();

        return clazz.getSimpleName();
    }

    public record ResolvedSetter(Method setter, Class<?> beanType) {}
}
