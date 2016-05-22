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

package com.predic8.membrane.core.lang.java;

import com.google.common.base.Function;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.lang.LanguageSupport;
import com.predic8.membrane.core.lang.ScriptExecutorPool;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.Map;

public class JavascriptLanguageSupport extends LanguageSupport {

    private static final Log log = LogFactory.getLog(JavascriptLanguageSupport.class);

    private abstract class JavascriptScriptExecutorPool<R> extends ScriptExecutorPool<ScriptEngine,R>{
        private final String javascriptCode;

        ScriptEngineManager sce = new ScriptEngineManager();
        final static String javascriptEngineName = "nashorn";

        private JavascriptScriptExecutorPool(Router router, String expression) {
            this.javascriptCode = expression;
            init(router);
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
    public Function<Map<String, Object>, Boolean> compileExpression(Router router, String src) {
        return new JavascriptScriptExecutorPool<Boolean>(router, getScriptWithImports(src)) {
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
    public Function<Map<String, Object>, Object> compileScript(Router router, String script) {
        return new JavascriptScriptExecutorPool<Object>(router, getScriptWithImports(script)) {
            @Override
            public Object apply(Map<String, Object> parameters) {
                return this.execute(parameters);
            }
        };
    }

    private String getScriptWithImports(String src) {
        return src;
    }
}
