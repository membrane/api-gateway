/*
 *  Copyright 2023 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.javascript;

import com.predic8.membrane.core.lang.ScriptError;
import com.predic8.membrane.core.lang.javascript.GraalVMJavascriptSupport;
import com.predic8.membrane.core.router.Router;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class GraalVMJavascriptLanguageAdapter extends LanguageAdapter {

    public GraalVMJavascriptLanguageAdapter(Router router) {
        super(router);
        languageSupport = new GraalVMJavascriptSupport();
    }

    /**
     * Host exceptions are thrown by Java code the script calls. Their location is the line of the call.
     */
    @Override
    public ScriptError describe(Exception e) {
        if (!(e instanceof PolyglotException pe))
            return new ScriptError(false, null, null, Map.of("engine", "graal"));

        final var location = getLocationInScript(pe);
        return new ScriptError(!pe.isHostException(),
                location != null ? location.getStartLine() - preScriptLineLength : null,
                location != null ? location.getStartColumn() : null,
                Map.of("engine", "graal"));
    }

    /**
     * Code passed to eval() or new Function() has a source of its own. Its lines are not lines of the script, so
     * the innermost frame in the script is used instead, e.g. the line calling eval().
     */
    private static @Nullable SourceSection getLocationInScript(PolyglotException pe) {
        if (isInScript(pe.getSourceLocation()))
            return pe.getSourceLocation();
        for (final var frame : pe.getPolyglotStackTrace()) {
            if (isInScript(frame.getSourceLocation()))
                return frame.getSourceLocation();
        }
        return null;
    }

    private static boolean isInScript(@Nullable SourceSection location) {
        return location != null && GraalVMJavascriptSupport.SCRIPT_NAME.equals(location.getSource().getName());
    }

    protected String getPreScript() {
        return """
                var FileClass = Java.type("java.io.File");
                var Request = Java.type("com.predic8.membrane.core.http.Request");
                var Response = Java.type("com.predic8.membrane.core.http.Response");
                """;
    }


}
