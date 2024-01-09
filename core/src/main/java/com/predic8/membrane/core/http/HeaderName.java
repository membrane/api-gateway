/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.http;

import static java.util.Objects.hash;

/**
 * This class is used by {@link Header} a key for header fields.
 * The {@link #hashCode()} method is overridden so the keys are
 * not case sensitive (as per the HTTP spec).
 */
public class HeaderName {

	private final String name;

	public HeaderName(String name) {
		this.name = name.toLowerCase();
	}

	@Override
	public boolean equals(Object obj) {
		return (this == obj) || (obj instanceof HeaderName) && (name.equals((((HeaderName) obj).name)));
	}

	public boolean equalsString(String str) {
		return name.equalsIgnoreCase(str);
	}

	@Override
	public int hashCode() {
		return hash(name);
	}

	@Override
	public String toString() {
		return name;
	}
}
