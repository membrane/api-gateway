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
import com.predic8.membrane.core.lang.ScriptExecutorPool;
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
        }
    };

    private abstract static class JavascriptScriptExecutorPool<R> extends ScriptExecutorPool<Script,R>{
        private final String javascriptCode;

        private JavascriptScriptExecutorPool(String expression) {
            this.javascriptCode = expression;
            init();
        }

        @Override
        protected Object invoke(Script script, Map<String, Object> parameters) {
            try (var cx = contextFactory.enterContext()) {
                // Fresh standard objects per execution: a previous exchange must not leave values behind,
                // neither in variables nor on built-in objects like Object.prototype
                final var scope = cx.initStandardObjects();
                parameters.forEach((name, value) -> ScriptableObject.putProperty(scope, name, Context.javaToJS(value, scope)));
                return Context.jsToJava(script.exec(cx, scope, scope), Object.class);
            } catch (RhinoException re) {
                final var e = new ScriptException(re.getMessage(), re.sourceName(), re.lineNumber(), re.columnNumber());
                log.error("Error compiling script: {}", e.getMessage());
                log.error("Error in Line: {}", getLineFromMultilineString(javascriptCode,e.getLineNumber()));
                log.error("Script:\n{}", javascriptCode);
                throw new RuntimeException("Error compiling script:", e);
            }
        }

        @Override
        protected Script createOneScript() {
            try (var cx = contextFactory.enterContext()) {
                // Lines are counted from 0 as by the JSR 223 engine, RhinoJavascriptLanguageAdapter relies on it
                return cx.compileString(javascriptCode, "eval", 0, null);
            }
        }
    }

    @Override
    public Function<Map<String, Object>, Object> compileScript(ClassLoader classLoader, String script) {
        return new JavascriptScriptExecutorPool<>(script) {
            @Override
            public Object apply(Map<String, Object> parameters) {
                return this.execute(parameters);
            }
        };
    }
}
