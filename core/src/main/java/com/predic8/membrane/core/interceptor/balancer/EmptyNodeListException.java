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

package com.predic8.membrane.core.interceptor.balancer;

/**
 * This is thrown if there is no Node available right now.
 *
 * <p>Possible causes:
 * <pre>
 *  - Misconfiguration, no node was configured at all.
 *  - All nodes went down during runtime, detected by external monitoring like Nagios/Zabbix, and they were
 *    removed externally through the membrane rest api.
 * </pre></p>
 */
public class EmptyNodeListException extends Exception {

	private static final long serialVersionUID = -1239983654002876857L;

	public EmptyNodeListException() {
		super("Node list empty.");
	}
}
