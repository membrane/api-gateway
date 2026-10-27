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

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public class RhinoJavascriptLanguageSupport implements LanguageSupport {

    /**
     * File name of the script. Code passed to eval() or new Function() gets a different name, e.g.
     * "membrane-script.js#12(eval)".
     */
    public static final String SCRIPT_NAME = "membrane-script.js";

    private static final Pattern POSITION = Pattern.compile(
            "(?: \\(\\S+#\\d+\\))?(?: in \\S+ at line number -?\\d+(?: at column number -?\\d+)?)?$");

    private abstract static class JavascriptScriptExecutorPool<R> extends ScriptExecutorPool<ScriptEngine,R>{
        private final String javascriptCode;

        protected final ScriptEngineManager sce;
        protected final static String javascriptEngineName = "rhino";

        private JavascriptScriptExecutorPool(ClassLoader classLoader, String expression) {
            this.javascriptCode = expression;

            // The ScriptEngineManager should search the engine in the classloader of the router
            // otherwise the engine will not be found
            sce = new ScriptEngineManager(classLoader);

            init();
        }

        @Override
        protected Object invoke(ScriptEngine script, Map<String, Object> parameters) {
            parameters.forEach(script::put);
            try {
                return script.eval(javascriptCode);
            } catch (ScriptException e) {
                // Logged by the caller
                throw new RhinoScriptException(withoutPosition(e), e.getFileName(), e.getLineNumber(), e.getColumnNumber());
            } finally {
                // The engine is pooled. A parameter missing in the next call must not keep this value.
                parameters.keySet().forEach(script.getBindings(ScriptContext.ENGINE_SCOPE)::remove);
            }
        }

        /**
         * Rhino and ScriptException append the position counted with the pre-script, e.g.
         * " (membrane-script.js#10) in membrane-script.js at line number 10 at column number 0". The caller reports
         * the line in the script.
         */
        private static String withoutPosition(ScriptException e) {
            return POSITION.matcher(e.getMessage()).replaceFirst("");
        }

        @Override
        protected ScriptEngine createOneScript() {
            synchronized (sce){
                final var engine = sce.getEngineByName(javascriptEngineName);
                engine.put(ScriptEngine.FILENAME, SCRIPT_NAME);
                return engine;
            }
        }
    }

    @Override
    public Function<Map<String, Object>, Object> compileScript(ClassLoader classLoader, String script) {
        return new JavascriptScriptExecutorPool<>(classLoader, script) {
            @Override
            public Object apply(Map<String, Object> parameters) {
                return this.execute(parameters);
            }
        };
    }
}