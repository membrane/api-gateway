/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
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

import com.predic8.membrane.core.lang.*;
import org.graalvm.polyglot.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class GraalVMJavascriptSupport extends LanguageSupport {

    private static final Logger log = LoggerFactory.getLogger(GraalVMJavascriptSupport.class);

    private abstract static class GraalVMJavascriptExecutorPool<R> extends ScriptExecutorPool<Source, R> {

        // Permissions are needed to access the Java Host from Javascript e.g. access to Exchange
        private static final Context context = Context.newBuilder()
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(s -> true)
                .allowPolyglotAccess(PolyglotAccess.ALL).build();

        private final String javascriptCode;

        private GraalVMJavascriptExecutorPool(ExecutorService executorService, String expression) {
            this.javascriptCode = expression;
            init(executorService);
        }

        @Override
        protected Object invoke(Source source, Map<String, Object> parameters) {
            try {
                synchronized (context) {
                    for (String name : parameters.keySet()) {
                        context.getBindings("js").putMember(name, parameters.get(name));
                    }

                    // Eval caches the compiled script internally. No need to call parse and try to
                    // cache it in Membrane.
                    Value js = context.eval(source);

                    if (js.isHostObject())
                        return js.asHostObject();
                    return js;
                }
            } catch (PolyglotException e) {
                if (e.isSyntaxError()) {
                    SourceSection location = e.getSourceLocation();
                    log.warn("Syntax error compiling Javascript at {} line {} position {}", location.getSource(), location.getStartLine(), location.getCharIndex());
                    log.warn("Location " + location);
                } else {
                    log.warn("");
                }
                throw e;
            } catch (Exception e) {
                log.error("Error compiling script:", e);
                throw new RuntimeException("Error compiling script:", e);
            }
        }

        @Override
        protected Source createOneScript() {
            return Source.create("js", javascriptCode);
        }
    }

    @Override
    public Function<Map<String, Object>, Boolean> compileExpression(ExecutorService executorService, ClassLoader cl, String src) {
        return new GraalVMJavascriptSupport.GraalVMJavascriptExecutorPool<>(executorService, src) {
            @Override
            public Boolean apply(Map<String, Object> parameters) {
                Object result = this.execute(parameters);
                if (result instanceof Boolean)
                    return (Boolean) result;
                return false;
            }
        };
    }

    @Override
    public Function<Map<String, Object>, Object> compileScript(ExecutorService executorService, ClassLoader cl, String script) {
        return new GraalVMJavascriptSupport.GraalVMJavascriptExecutorPool<>(executorService, script) {
            @Override
            public Object apply(Map<String, Object> parameters) {
                return this.execute(parameters);
            }
        };
    }
}