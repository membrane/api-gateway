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

import com.predic8.membrane.core.lang.LanguageSupport;
import com.predic8.membrane.core.lang.ScriptExecutorPool;
import org.graalvm.polyglot.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class GraalVMJavascriptSupport implements LanguageSupport {

    private static final Logger log = LoggerFactory.getLogger(GraalVMJavascriptSupport.class);

    private abstract static class GraalVMJavascriptExecutorPool<R> extends ScriptExecutorPool<Source, R> {

        // Shared by all contexts, so that a script is only compiled once
        private static final Engine engine = Engine.create("js");

        private final String javascriptCode;

        private GraalVMJavascriptExecutorPool(String expression) {
            this.javascriptCode = expression;
            init();
        }

        @Override
        protected Object invoke(Source source, Map<String, Object> parameters) {
            // A fresh context per execution: globals of a previous exchange, including the script's own
            // top-level variables, must not be visible.
            // Permissions are needed to access the Java Host from Javascript e.g. access to Exchange
            try (var context = Context.newBuilder("js")
                    .engine(engine)
                    .allowHostAccess(HostAccess.ALL)
                    .allowHostClassLookup(s -> true)
                    .allowPolyglotAccess(PolyglotAccess.ALL).build()) {
                final var bindings = context.getBindings("js");
                parameters.forEach(bindings::putMember);

                // Values of the context are not usable after it is closed
                return toJava(context.eval(source));
            } catch (PolyglotException e) {
                if (e.isSyntaxError()) {
                    SourceSection location = e.getSourceLocation();
                    log.warn("Syntax error compiling Javascript at {} line {} position {}", location.getSource(), location.getStartLine(), location.getCharIndex());
                    log.warn("Location: {}", location);
                } else {
                    log.warn("",e);
                }
                throw e;
            } catch (Exception e) {
                log.error("Error compiling script:", e);
                throw new RuntimeException("Error compiling script:", e);
            }
        }

        private static Object toJava(Value value) {
            if (value.isNull() || value.canExecute())
                return null;
            if (value.isHostObject())
                return value.asHostObject();
            if (value.isString())
                return value.asString();
            if (value.isBoolean())
                return value.asBoolean();
            if (value.fitsInLong())
                return value.asLong();
            if (value.isNumber())
                return value.asDouble();
            if (value.hasArrayElements()) {
                final var list = new ArrayList<>();
                for (long i = 0; i < value.getArraySize(); i++)
                    list.add(toJava(value.getArrayElement(i)));
                return list;
            }
            if (value.hasMembers()) {
                final var map = new LinkedHashMap<String, Object>();
                for (final var key : value.getMemberKeys())
                    map.put(key, toJava(value.getMember(key)));
                return map;
            }
            return value.toString();
        }

        @Override
        protected Source createOneScript() {
            return Source.create("js", javascriptCode);
        }
    }

    @Override
    public Function<Map<String, Object>, Object> compileScript(ClassLoader cl, String script) {
        return new GraalVMJavascriptSupport.GraalVMJavascriptExecutorPool<>(script) {
            @Override
            public Object apply(Map<String, Object> parameters) {
                return execute(parameters);
            }
        };
    }
}