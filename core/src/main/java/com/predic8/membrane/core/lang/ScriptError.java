/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.lang;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * What a script language knows about an exception thrown while running a script.
 *
 * @param scriptException true if the exception is raised by the script language, e.g. a missing property or a
 *                        reference error. For other exceptions, e.g. thrown by Java code the script calls, the
 *                        stacktrace is logged.
 * @param line            1-based line in the script as written by the user, or null if unknown. A line below 1 is
 *                        in code Membrane adds before the script and becomes null.
 * @param column          1-based column, or null if unknown. Null if the line is unknown.
 * @param details         Language specific fields for the ProblemDetails
 */
public record ScriptError(boolean scriptException, @Nullable Integer line, @Nullable Integer column,
                          Map<String, Object> details) {

    public ScriptError {
        if (line != null && line < 1)
            line = null;
        if (line == null || (column != null && column < 1))
            column = null;
        details = Map.copyOf(details);
    }
}
