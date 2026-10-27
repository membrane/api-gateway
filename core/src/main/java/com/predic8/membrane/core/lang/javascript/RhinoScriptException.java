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

package com.predic8.membrane.core.lang.javascript;

import org.jetbrains.annotations.Nullable;

/**
 * Error raised by Rhino while running a script. The message is without the position, which Rhino counts including
 * the pre-script. The ScriptException is not kept as cause because its message would add the position again.
 *
 * @param fileName Name of the source, SCRIPT_NAME for the script itself or e.g. "membrane-script.js#12(eval)"
 * @param line     0-based line in the source including the pre-script
 * @param column   Column, 0 if unknown
 */
public class RhinoScriptException extends RuntimeException {

    private final @Nullable String fileName;
    private final int line;
    private final int column;

    public RhinoScriptException(String message, @Nullable String fileName, int line, int column) {
        super(message);
        this.fileName = fileName;
        this.line = line;
        this.column = column;
    }

    public @Nullable String getFileName() {
        return fileName;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
