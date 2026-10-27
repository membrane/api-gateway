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
import com.predic8.membrane.core.lang.javascript.RhinoJavascriptLanguageSupport;
import com.predic8.membrane.core.lang.javascript.RhinoScriptException;
import com.predic8.membrane.core.router.Router;

import java.util.Map;

public class RhinoJavascriptLanguageAdapter extends LanguageAdapter {

    public RhinoJavascriptLanguageAdapter(Router router) {
        super(router);
        languageSupport = new RhinoJavascriptLanguageSupport();
    }

    /**
     * Rhino reports a Java exception thrown by code the script calls with the message only, so it is treated like
     * an error in the script.
     */
    @Override
    public ScriptError describe(Exception e) {
        if (!(e instanceof RhinoScriptException rse))
            return new ScriptError(false, null, null, Map.of("engine", "rhino"));

        // Lines of code passed to eval() or new Function() are not lines of the script
        if (!RhinoJavascriptLanguageSupport.SCRIPT_NAME.equals(rse.getFileName()))
            return new ScriptError(true, null, null, Map.of("engine", "rhino"));

        // Rhino counts lines from 0. A column of 0 means unknown.
        return new ScriptError(true, rse.getLine() - preScriptLineLength + 1, rse.getColumn(), Map.of("engine", "rhino"));
    }

    @Override
    protected String getPreScript() {
        return """
            var imports = new JavaImporter(com.predic8.membrane.core.interceptor.Outcome, com.predic8.membrane.core.http)
            var console = {};
            console.log = function(s) {
              java.lang.System.out.println(new java.lang.String(s));
            };
            var json;
            if (message.isJSON()) {
              json=JSON.parse(message.getBodyAsStringDecoded());
            }
            with(imports) {
            """;
    }

    @Override
    protected String getPostScript() {
        return """ 
            };
            """;
    }
}
