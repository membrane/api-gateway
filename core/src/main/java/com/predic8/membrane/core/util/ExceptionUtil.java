/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.util;

public class ExceptionUtil {

    /**
     * Concatenates the messages of all nested exceptions.
     *
     * This could be improved to make the resulting String more dense in case of repeated information parts.
     *
     * @param throwable the exception
     * @return a String containing all messages of nested exceptions
     */
    public static String concatMessageAndCauseMessages(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        boolean causedBy = false;
        do {
            boolean skip = sb.toString().contains(throwable.getMessage());
            if (!skip) {
                if (causedBy) {
                    sb.append(" caused by: ");
                    causedBy = false;
                }
                sb.append(throwable.getMessage());
            }
            throwable = throwable.getCause();
            if (throwable != null && !skip) {
                causedBy = true;
            }
        } while (throwable != null);
        return sb.toString();
    }

    public static Throwable getRootCause(Throwable t) {
        if (t == null)
            return null;
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
