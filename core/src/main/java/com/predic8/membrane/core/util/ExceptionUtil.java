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

import com.predic8.membrane.core.util.xml.parser.*;

import java.util.*;

public class ExceptionUtil {
    public static String concatMessageAndCauseMessages(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        do {
            sb.append(throwable.getMessage());
            throwable = throwable.getCause();
            if (throwable != null) {
                sb.append(" caused by: ");
            }
        } while (throwable != null);
        return sb.toString();
    }

    public static Throwable getRootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
