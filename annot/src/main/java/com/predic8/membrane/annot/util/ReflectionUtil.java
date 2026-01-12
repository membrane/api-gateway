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

package com.predic8.membrane.annot.util;

public class ReflectionUtil {

    /**
     * Converts a string literal to the target Java type.
     */
    public static Object convert(String raw, Class<?> targetType) {
        if (targetType == String.class) return raw;
        if (raw == null) {
            if (targetType.isPrimitive())
                throw new IllegalArgumentException("Cannot assign null to primitive " + targetType.getName());
            return null;
        }

        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(raw);
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(raw);
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(raw);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(raw);
        if (targetType == float.class || targetType == Float.class) return Float.parseFloat(raw);
        if (targetType == short.class || targetType == Short.class) return Short.parseShort(raw);
        if (targetType == byte.class || targetType == Byte.class) return Byte.parseByte(raw);
        if (targetType == char.class || targetType == Character.class) {
            if (raw.length() != 1) throw new IllegalArgumentException("Expected single character, got: " + raw);
            return raw.charAt(0);
        }

        if (targetType.isEnum()) {
            //noinspection unchecked
            return Enum.valueOf((Class<? extends Enum>) targetType, raw);
        }

        throw new IllegalArgumentException("Unsupported conversion to " + targetType.getName() + " from value: " + raw);
    }

    /**
     * Determines if the given wrapper class is the corresponding wrapper type
     * for the specified primitive type.
     *
     * @param primitive the primitive type to be checked (e.g., int.class, double.class)
     * @param wrapper the wrapper class to be checked (e.g., Integer.class, Double.class)
     * @return true if the wrapper class is the corresponding wrapper for the primitive type, false otherwise
     */
    public static boolean isWrapperOfPrimitive(Class<?> primitive, Class<?> wrapper) {
        return (primitive == int.class && wrapper == Integer.class)
               || (primitive == long.class && wrapper == Long.class)
               || (primitive == boolean.class && wrapper == Boolean.class)
               || (primitive == double.class && wrapper == Double.class)
               || (primitive == float.class && wrapper == Float.class)
               || (primitive == short.class && wrapper == Short.class)
               || (primitive == byte.class && wrapper == Byte.class)
               || (primitive == char.class && wrapper == Character.class);
    }
}
