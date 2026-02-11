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

package com.predic8.membrane.common;

public class ExceptionUtil {

    public static Throwable getRootCause(Throwable t) {
        if (t == null)
            return null;
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    public static <T extends Throwable> T getRootCause(Throwable t, Class<T> expected) {
        var e = getRootCause(t);
        if (e != null && expected.isInstance(e)) {
            return expected.cast(e);
        }
        throw new AssertionError("Expected %s but was %s".formatted(expected.getName(), e));
    }
}
