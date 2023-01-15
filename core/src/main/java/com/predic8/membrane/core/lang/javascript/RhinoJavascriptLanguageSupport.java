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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.lang.*;
import org.slf4j.*;

import javax.script.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class RhinoJavascriptLanguageSupport extends LanguageSupport {

    private static final Logger log = LoggerFactory.getLogger(RhinoJavascriptLanguageSupport.class);


    private abstract static class JavascriptScriptExecutorPool<R> extends ScriptExecutorPool<ScriptEngine,R>{
        private final String javascriptCode;

        protected final ScriptEngineManager sce;
        protected final static String javascriptEngineName = "rhino";

        private JavascriptScriptExecutorPool(ExecutorService executorService, ClassLoader classLoader, String expression) {
            this.javascriptCode = expression;

            // The ScriptEngineManager should search the engine in the classloader of the router
            // otherwise the engine will not be found
            sce = new ScriptEngineManager(classLoader);

            init(executorService);
        }

        @Override
        protected Object invoke(ScriptEngine script, Map<String, Object> parameters) {
            for(String name : parameters.keySet())
                script.put(name,parameters.get(name));
            try {
                return script.eval(javascriptCode);
            } catch (ScriptException e) {
                log.error("Error compiling script:", e);
                throw new RuntimeException("Error compiling script:", e);
            }
        }

        @Override
        protected ScriptEngine createOneScript() {
            synchronized (sce){
                return sce.getEngineByName(javascriptEngineName);
            }
        }
    }

    @Override
    public Function<Map<String, Object>, Boolean> compileExpression(ExecutorService executorService, ClassLoader classLoader, String src) {
        return new JavascriptScriptExecutorPool<>(executorService, classLoader, src) {
            @Override
            public Boolean apply(Map<String, Object> parameters) {
                Object result = this.execute(parameters);
                if (result instanceof Boolean)
                    return (Boolean)result;
                return false;
            }
        };
    }

    @Override
    public Function<Map<String, Object>, Object> compileScript(ExecutorService executorService, ClassLoader classLoader, String script) {
        return new JavascriptScriptExecutorPool<>(executorService, classLoader, script) {
            @Override
            public Object apply(Map<String, Object> parameters) {
                return this.execute(parameters);
            }
        };
    }
}
