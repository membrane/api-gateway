/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.cloud.etcd;

public class EtcdUtil {
	private static boolean isInRange(int minInclusive, int maxExclusive, int value) {
		return value >= minInclusive && value < maxExclusive;
	}

	public static EtcdRequest createBasicRequest(String url, String baseKey, String module) {
		return new EtcdRequest().url(url).baseKey(baseKey).module(module);
	}

	public static boolean checkStatusCode(int minInc, int maxExc, EtcdResponse resp) {
		if (!isInRange(minInc, maxExc, resp.getStatusCode())) {
			return false;
		}
		return true;
	}

	public static boolean checkOK(EtcdResponse resp) {
		return checkStatusCode(200, 300, resp);
	}
}
