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

import java.io.EOFException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;

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
            boolean skip = throwable.getMessage() == null || sb.toString().contains(throwable.getMessage());
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

    /**
     * Whether the throwable was (ultimately) caused by the peer going away, e.g. a client aborting an
     * upload. Such events are normal operation and should not be logged as errors.
     * <p>
     * Deliberately type-based: a plain {@link java.io.IOException} is not treated as a disconnect,
     * because it can just as well indicate a genuine fault.
     */
    public static boolean isPeerDisconnect(Throwable t) {
        Throwable root = getRootCause(t);
        return root instanceof ClosedChannelException
                || root instanceof SocketException
                || root instanceof EOFException;
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
