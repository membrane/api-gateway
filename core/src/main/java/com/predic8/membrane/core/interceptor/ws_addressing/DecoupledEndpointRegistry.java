/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.ws_addressing;

import java.util.HashMap;
import java.util.Map;

public class DecoupledEndpointRegistry {
	private final Map<String, String> registry = new HashMap<String, String>();

	public synchronized void register(String id, String url) {
		registry.put(id, url);
	}

	public synchronized String lookup(String id) {
		return registry.get(id);
	}

	@Override
	public synchronized String toString() {
		return "DecoupledEndpointRegistry: " + registry.toString();
	}
}