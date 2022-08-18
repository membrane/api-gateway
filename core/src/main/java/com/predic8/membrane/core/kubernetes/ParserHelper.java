/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.kubernetes;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCTextContent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;

public class ParserHelper {
    public static <T> void setSetter(T instance, Method method, Object value) throws InvocationTargetException, IllegalAccessException {
        method.invoke(instance, value);
    }

    public static boolean isStructured(Method method) {
        return findAnnotation(method, MCChildElement.class) != null;
    }

    private static boolean isCollection(Method method) {
        return Arrays.asList(method.getParameterTypes()).contains(List.class);
    }

    public static boolean isSetter(Method method) {
        return method.getName().startsWith("set");
    }

    public static boolean matchesJsonKey(Method method, String key) {
        return matchesJsonChildElementKey(method, key)
                || equalsTextContent(method, key)
                || equalsAttributeName(method, key);
    }

    private static boolean matchesJsonChildElementKey(Method method, String key) {
        MCChildElement annotation = findAnnotation(method, MCChildElement.class);
        return method.getName().substring(3).equalsIgnoreCase(key) && annotation != null;
    }

    private static boolean equalsTextContent(Method method, String key) {
        MCTextContent annotation = findAnnotation(method, MCTextContent.class);
        return method.getName().substring(3).equalsIgnoreCase(key) && annotation != null;
    }

    private static boolean equalsAttributeName(Method method, String key) {
        MCAttribute annotation = findAnnotation(method, MCAttribute.class);
        if (annotation == null)
            return false;
        return method.getName().substring(3).equalsIgnoreCase(key) && "".equals(annotation.attributeName())
                || annotation.attributeName().equals(key);
    }

}
