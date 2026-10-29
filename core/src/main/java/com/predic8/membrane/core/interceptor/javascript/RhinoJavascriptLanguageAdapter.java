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

import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.lang.javascript.RhinoJavascriptLanguageSupport;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import org.mozilla.javascript.RhinoException;

import javax.script.ScriptException;
import java.util.Map;
import java.util.function.Function;

import static com.predic8.membrane.core.util.text.TextUtil.getLineFromMultilineString;

public class RhinoJavascriptLanguageAdapter extends LanguageAdapter {

    public RhinoJavascriptLanguageAdapter(Router router) {
        super(router);
        languageSupport = new RhinoJavascriptLanguageSupport();
    }

    /**
     * Rhino compiles the script at startup. A syntax error reports the line of the script, not of the script
     * with the pre-script.
     */
    @Override
    public Function<Map<String, Object>, Object> compileScript(String script) {
        try {
            return super.compileScript(script);
        } catch (RhinoException e) {
            // Without the cause, whose message names the line of the script with the pre-script
            final var line = e.lineNumber() - preScriptLineLength + 1;
            throw new ConfigurationException("%s in line %d: %s".formatted(e.details(), line, getLineFromMultilineString(script, line)));
        }
    }

    @Override
    public ProblemDetails getProblemDetails(Exception e) {
        ProblemDetails pd = ProblemDetails.internal(router.getConfiguration().isProduction(),"javascript");
        if (e.getCause() instanceof ScriptException se) {
            pd.internal("column", se.getColumnNumber() + 1);
            pd.internal("line", se.getLineNumber() - preScriptLineLength + 1);
            pd.internal("message",  se.getMessage());
            pd.internal("engine", "rhino");
        }
        return pd;
    }

    @Override
    protected String getPreScript() {
        return """
            var imports = new JavaImporter(com.predic8.membrane.core.interceptor.Outcome, com.predic8.membrane.core.http)
            var console = {};
            console.log = function() {
              java.lang.System.out.println(new java.lang.String(Array.prototype.map.call(arguments, function(a) {
                if (typeof a === 'object' && a !== null && !(a instanceof java.lang.Object) && !(a instanceof Error)) {
                  try { var s = JSON.stringify(a); if (s !== undefined) return s; } catch (e) { }
                }
                return String(a);
              }).join(' ')));
            };
            var print = console.log;
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
