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

package com.predic8.membrane.core.lang;

import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public abstract class ScriptExecutorPool<T, R> implements Function<Map<String, Object>, R> {
	private static final Logger log = LoggerFactory.getLogger(ScriptExecutorPool.class);

	private static final int concurrency = Runtime.getRuntime().availableProcessors() * 2;
	ArrayBlockingQueue<T> scripts = new ArrayBlockingQueue<>(concurrency);

	public void init(ExecutorService executorService) {
		scripts.add(createOneScript());
		executorService.execute(() -> {
			try {
				for (int i = 1; i < concurrency; i++)
					scripts.add(createOneScript());
			} catch (Exception e) {
				log.error("Error compiling script:", e);
			}
		});
	}

	public final Object execute(Map<String, Object> parameters) {
		try {
			T script = scripts.take();
			try {
				return invoke(script, parameters);
			} finally {
				scripts.put(script);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	protected abstract Object invoke(T script, Map<String, Object> parameters);
	protected abstract T createOneScript();

}
