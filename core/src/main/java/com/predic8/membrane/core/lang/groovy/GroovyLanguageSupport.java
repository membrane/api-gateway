/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.lang.groovy;

import com.predic8.membrane.core.lang.*;
import groovy.lang.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class GroovyLanguageSupport extends LanguageSupport {

	private abstract static class GroovyScriptExecutorPool<R> extends
	ScriptExecutorPool<Script, R> {
		private final String groovyCode;

		private GroovyScriptExecutorPool(ExecutorService executorService, String expression) {
			this.groovyCode = expression;
			init(executorService);
		}

		@Override
		protected Script createOneScript() {
			synchronized (shell) {
				return shell.parse(groovyCode);
			}
		}

		@Override
		protected Object invoke(Script script, Map<String, Object> parameters) {
			script.setBinding(getBinding(parameters));
			return script.run();
		}

		private static Binding getBinding(Map<String, Object> parameters) {
			Binding b = new Binding();
			for (Map.Entry<String, Object> parameter : parameters.entrySet())
				b.setVariable(parameter.getKey(), parameter.getValue());
			return b;
		}
	}

	private static final GroovyShell shell = new GroovyShell();

	@Override
	public Function<Map<String, Object>, Boolean> compileExpression(ExecutorService executorService, ClassLoader classLoader, String src) {
		return new GroovyScriptExecutorPool<>(executorService, addImports(src)) {
			@Override
			public Boolean apply(Map<String, Object> parameters) {
				if (this.execute(parameters) instanceof Boolean result)
					return result;
				return false;
			}
		};
	}

	@Override
	public Function<Map<String, Object>, Object> compileScript(ExecutorService executorService, ClassLoader classLoader, String script) {
		return new GroovyScriptExecutorPool<>(executorService, addImports(script)) {
			@Override
			public Object apply(Map<String, Object> parameters) {
				return this.execute(parameters);
			}
		};
	}

	private String addImports(String src) {
		return """
				import static com.predic8.membrane.core.interceptor.Outcome.*
				import com.predic8.membrane.core.http.*
				""" + src;
	}
}