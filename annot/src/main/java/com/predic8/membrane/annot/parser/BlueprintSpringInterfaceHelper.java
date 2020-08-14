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
package com.predic8.membrane.annot.parser;

import org.osgi.service.blueprint.container.BlueprintContainer;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;

/**
 * Helper class for simulating the Spring context startup order:
 * <ol>
 * <li>Call constructor</li>
 * <li>Set properties (possibly child beans, themselves fully initialized)</li>
 * <li>call {@link ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)},
 * if the bean implements {@link ApplicationContextAware}</li>
 * <li>call {@link Lifecycle#start()}, if the bean implements {@link Lifecycle}.</li>
 * </ol>
 *
 * To avoid calling {@link ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)}
 * on a bean A before all other properties have been set on it (step 2), an instance B of this class is created. "B.client = A"
 * and "A depends on B" are the relationships modeled using Blueprint. This causes "B.setClient(A)" to be executed
 * <ul>
 * <li>after all other properties have been set on A</li>
 * <li>before A is returned by {@link BlueprintContainer#getComponentInstance(String)} or used as a child bean</li>
 * </ul>
 * which fulfills our requirements.
 */
public class BlueprintSpringInterfaceHelper {

	private Object client;

	BlueprintContainer blueprintContainer;

	public BlueprintContainer getBlueprintContainer() {
		return blueprintContainer;
	}

	public void setBlueprintContainer(BlueprintContainer blueprintContainer) {
		this.blueprintContainer = blueprintContainer;
	}

	public Object getClient() {
		return client;
	}

	public void setClient(Object client) {
		this.client = client;
	}

	public void init() {
		if (client instanceof ApplicationContextAware) {
			((ApplicationContextAware)client).setApplicationContext(new BlueprintSimulatedSpringApplicationContext(blueprintContainer));
		}
		if (client instanceof Lifecycle) {
			((Lifecycle)client).start();
		}
	}

	public void destroy() {
		if (client instanceof Lifecycle) {
			((Lifecycle)client).stop();
		}
	}

}
