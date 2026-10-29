/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.lang.javascript;

import com.predic8.membrane.core.lang.LanguageSupport;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptException;
import java.util.Map;
import java.util.function.Function;

import static com.predic8.membrane.core.util.text.TextUtil.getLineFromMultilineString;

/**
 * Uses the Rhino API instead of the JSR 223 engine, which keeps the standard objects such as Object.prototype
 * of all executions in one scope.
 */
public class RhinoJavascriptLanguageSupport implements LanguageSupport {

    private static final Logger log = LoggerFactory.getLogger(RhinoJavascriptLanguageSupport.class);

    // More than the about 7700 calls the compiled mode allowed with the default stack size of a thread
    private static final int MAX_STACK_DEPTH = 10_000;

    // Configured as the context factory of the JSR 223 engine of rhino-engine
    private static final ContextFactory contextFactory = new ContextFactory() {
        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            if (featureIndex == Context.FEATURE_INTEGER_WITHOUT_DECIMAL_PLACE)
                return true;
            return super.hasFeature(cx, featureIndex);
        }

        @Override
        protected void onContextCreated(Context cx) {
            super.onContextCreated(cx);
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setGeneratingDebug(true);
            // The script is compiled for every execution. The interpreter compiles fast, while the compiled mode
            // generates and loads several classes for every compilation.
            cx.setInterpretedMode(true);
            // The interpreter keeps the frames of Javascript calls on the heap. Without a limit, an endless recursion
            // would use up the memory of the JVM instead of overflowing the stack of the thread.
            cx.setMaximumInterpreterStackDepth(MAX_STACK_DEPTH);
        }
    };

    @Override
    public Function<Map<String, Object>, Object> compileScript(ClassLoader classLoader, String script) {
        // Compiled once in advance, so that a syntax error is reported at startup
        try (var cx = contextFactory.enterContext()) {
            compile(cx, script);
        }
        return parameters -> execute(script, parameters);
    }

    private static Object execute(String javascriptCode, Map<String, Object> parameters) {
        try (var cx = contextFactory.enterContext()) {
            // A compiled script is not reused: it keeps objects created by an execution, such as the strings array
            // of a tagged template, together with the standard objects of that execution
            final var script = compile(cx, javascriptCode);
            // Fresh standard objects per execution: a previous exchange must not leave values behind,
            // neither in variables nor on built-in objects like Object.prototype
            final var scope = cx.initStandardObjects();
            parameters.forEach((name, value) -> ScriptableObject.putProperty(scope, name, Context.javaToJS(value, scope)));
            final var result = script.exec(cx, scope, scope);
            // jsToJava would convert undefined to the string "undefined"
            if (Undefined.isUndefined(result))
                return null;
            return Context.jsToJava(result, Object.class);
        } catch (RhinoException re) {
            final var e = new ScriptException(re.getMessage(), re.sourceName(), re.lineNumber(), re.columnNumber());
            log.error("Error executing script: {}", e.getMessage());
            // Rhino counts the lines from 0
            log.error("Error in Line: {}", getLineFromMultilineString(javascriptCode, e.getLineNumber() + 1));
            log.error("Script:\n{}", javascriptCode);
            throw new RuntimeException("Error executing script:", e);
        }
    }

    private static Script compile(Context cx, String javascriptCode) {
        // Lines are counted from 0 as by the JSR 223 engine, RhinoJavascriptLanguageAdapter relies on it
        return cx.compileString(javascriptCode, "eval", 0, null);
    }
}
