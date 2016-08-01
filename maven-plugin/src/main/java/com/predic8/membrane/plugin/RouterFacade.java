/*
 * Copyright 2016 predic8 GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.predic8.membrane.plugin;

import com.predic8.membrane.core.Router;
import org.apache.maven.plugin.MojoFailureException;

import java.net.MalformedURLException;
import java.util.concurrent.TimeUnit;

class RouterFacade {
	private final Router router;

	private RouterFacade(Router router) {
		this.router = router;
	}

	static RouterFacade createStarted(String proxiesPath) throws MojoFailureException {
		try {
			return new RouterFacade(Router.init(proxiesPath));
		} catch (MalformedURLException e) {
			throw new MojoFailureException("Failure", e);
		}
	}

	void stop() {
		router.stop();
	}

	void waitForFinish() {
		try {
			router.getBackgroundInitializator().awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}