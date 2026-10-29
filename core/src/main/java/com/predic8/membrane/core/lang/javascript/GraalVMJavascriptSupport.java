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

import java.util.*;
import java.util.function.Function;

public class GraalVMJavascriptSupport implements LanguageSupport {

    private static final Logger log = LoggerFactory.getLogger(GraalVMJavascriptSupport.class);

    private abstract static class GraalVMJavascriptExecutorPool<R> extends ScriptExecutorPool<Source, R> {

        // Same as the default nesting limit of Jackson, which serializes the result
        private static final int MAX_DEPTH = 1000;

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
                return toJava(context.eval(source), new HashSet<>());
            } catch (PolyglotException e) {
                if (e.isSyntaxError()) {
                    SourceSection location = e.getSourceLocation();
                    log.warn("Syntax error compiling Javascript at {} line {} position {}", location.getSource(), location.getStartLine(), location.getCharIndex());
                    log.warn("Location: {}", location);
                } else {
                    log.warn("",e);
                }
                throw e;
            } catch (ScriptResultException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error compiling script:", e);
                throw new RuntimeException("Error compiling script:", e);
            }
        }

        /**
         * @param ancestors values on the path from the result to value, to detect cycles
         */
        private static Object toJava(Value value, Set<Value> ancestors) {
            if (value.isNull() || value.canExecute())
                return null;
            if (value.isHostObject())
                return hostToJava(value, ancestors);
            if (value.isString())
                return value.asString();
            if (value.isBoolean())
                return value.asBoolean();
            if (value.fitsInLong())
                return value.asLong();
            if (value.isNumber())
                return value.asDouble();
            if (value.hasArrayElements()) {
                enter(value, ancestors);
                final var list = new ArrayList<>();
                for (long i = 0; i < value.getArraySize(); i++)
                    list.add(toJava(value.getArrayElement(i), ancestors));
                ancestors.remove(value);
                return list;
            }
            if (value.hasMembers()) {
                enter(value, ancestors);
                final var map = new LinkedHashMap<String, Object>();
                for (final var key : value.getMemberKeys())
                    map.put(key, toJava(value.getMember(key), ancestors));
                ancestors.remove(value);
                return map;
            }
            return value.toString();
        }

        /**
         * Java maps, collections and arrays can contain Javascript objects, e.g. json.put('a', {b: 1}), so they are
         * copied. Collections and arrays become lists.
         */
        private static Object hostToJava(Value value, Set<Value> ancestors) {
            final var object = value.asHostObject();
            if (object instanceof Map<?, ?> map) {
                enter(value, ancestors);
                final var copy = new LinkedHashMap<>();
                map.forEach((key, element) -> copy.put(elementToJava(value.getContext(), key, ancestors), elementToJava(value.getContext(), element, ancestors)));
                ancestors.remove(value);
                return copy;
            }
            if (object instanceof Collection<?> collection)
                return copyToList(value, collection, ancestors);
            if (object instanceof Object[] array)
                return copyToList(value, Arrays.asList(array), ancestors);
            return object;
        }

        private static List<Object> copyToList(Value value, Collection<?> elements, Set<Value> ancestors) {
            enter(value, ancestors);
            final var copy = new ArrayList<>();
            elements.forEach(element -> copy.add(elementToJava(value.getContext(), element, ancestors)));
            ancestors.remove(value);
            return copy;
        }

        private static Object elementToJava(Context context, Object element, Set<Value> ancestors) {
            final var value = context.asValue(element);
            if (value.isHostObject())
                return hostToJava(value, ancestors);
            if (value.hasMembers() || value.hasArrayElements() || value.canExecute())
                return toJava(value, ancestors);
            // Strings, numbers, ... are kept as they are, e.g. a BigDecimal of the parsed json
            return element;
        }

        private static void enter(Value value, Set<Value> ancestors) {
            if (ancestors.size() >= MAX_DEPTH)
                throw new ScriptResultException("Result of the script is nested deeper than %d levels.".formatted(MAX_DEPTH));
            if (!ancestors.add(value))
                throw new ScriptResultException("Result of the script contains a cycle.");
        }

        /**
         * Without a stack trace, as it would only repeat toJava up to MAX_DEPTH times.
         */
        private static class ScriptResultException extends RuntimeException {
            private ScriptResultException(String message) {
                super(message, null, false, false);
            }
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