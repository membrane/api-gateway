/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.rest;

import java.util.Map;
import java.util.regex.Matcher;

public class QueryParameter {
	private Map<String, String> params;
	private Matcher matcher;
	
	public QueryParameter(Map<String, String> params, Matcher matcher) {
		this.params = params;
		this.matcher = matcher;
	}
	
	public int getInt(String name, int def) {
		if (params.containsKey(name)) {
			return getInt(name);
		}
		return def;
	}
	
	public String getString(String name, String def) {
		if (params.containsKey(name)) {
			return getString(name);
		}
		return def;
	}

	public int getInt(String name) {
		return Integer.parseInt(params.get(name));
	}

	public long getLong(String name) {
		return Long.parseLong(params.get(name));
	}

	public String getString(String name) {
		return params.get(name);
	}

	public int getGroupInt(int i) {
		return Integer.parseInt(getGroup(i));
	}
	
	public String getGroup(int i) {
		return matcher.group(i);
	}

	public boolean has(String name) {
		return params.containsKey(name);
	}
	
}
