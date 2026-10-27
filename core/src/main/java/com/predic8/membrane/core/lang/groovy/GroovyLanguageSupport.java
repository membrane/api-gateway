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

import com.predic8.membrane.core.lang.LanguageSupport;
import com.predic8.membrane.core.lang.ScriptExecutorPool;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

public class GroovyLanguageSupport implements LanguageSupport {

	private abstract static class GroovyScriptExecutorPool<R> extends
	ScriptExecutorPool<Script, R> {
		private final String groovyCode;

		private GroovyScriptExecutorPool(String expression) {
			this.groovyCode = expression;
			init();
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

	private static final String IMPORTS = """
			import static com.predic8.membrane.core.interceptor.Outcome.*
			import com.predic8.membrane.core.http.*
			""";

	private static final int IMPORT_LINES = (int) IMPORTS.lines().count();

	/**
	 * Name of the source file Groovy assigns to a script parsed from a String
	 */
	private static final Pattern SCRIPT_FILE_NAME = Pattern.compile("Script\\d+\\.groovy");

	private static final GroovyShell shell = new GroovyShell(createCompilerConfiguration());

	private static CompilerConfiguration createCompilerConfiguration() {
		final var configuration = new CompilerConfiguration();
		configuration.setScriptBaseClass(BuiltInFunctionsScript.class.getName());
		return configuration;
	}

	@Override
	public Function<Map<String, Object>, Object> compileScript(ClassLoader classLoader, String script) {
		return new GroovyScriptExecutorPool<>(addImports(script)) {
			@Override
			public Object apply(Map<String, Object> parameters) {
				return this.execute(parameters);
			}
		};
	}

	private String addImports(String src) {
		return IMPORTS + src;
	}

	/**
	 * Finds the line of the script, as written by the user, at which the exception occurred.
	 *
	 * @param throwable Exception thrown while running a script compiled by this class
	 * @return 1-based line number in the user's script or empty if the exception has no script frame
	 */
	public static Optional<Integer> getScriptLineNumber(Throwable throwable) {
		for (var t = throwable; t != null; t = t.getCause()) {
			for (final var element : t.getStackTrace()) {
				if (element.getFileName() != null
					&& SCRIPT_FILE_NAME.matcher(element.getFileName()).matches()
					&& element.getLineNumber() > IMPORT_LINES) {
					return Optional.of(element.getLineNumber() - IMPORT_LINES);
				}
			}
		}
		return Optional.empty();
	}
}