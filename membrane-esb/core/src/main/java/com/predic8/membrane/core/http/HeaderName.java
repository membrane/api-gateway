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

/**
 * @author Ryan
 *
 *This class is used by the Header class as a key for header fields.
 *The hashCode method is overridden so the keys are not case sensitive
 *as in HTTP.
 *The toString method of this class overrides Object.toString and 
 *returns the name of the header field as a string
 * 
 */
public class HeaderName{
	
	private final String name;
	
	public HeaderName(String name) {
		this.name = name;
	}
	
	public HeaderName(HeaderName headerName) {
		name = headerName.name;
	}

	public boolean equals(Object obj) {
		if(!(obj instanceof HeaderName))
			return false;
		
		return name.equalsIgnoreCase(obj.toString());
	}

	public boolean equals(HeaderName other) {
		return name.equalsIgnoreCase(other.name);
	}

	public boolean equals(String s) {
		return name.equalsIgnoreCase(s);
	}
	
	public int hashCode() {
		return name.toLowerCase().hashCode();
	}
	
	public String toString() {
		return name;
	}
}
