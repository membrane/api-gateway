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

import com.predic8.membrane.core.Constants;

public class HeaderField {

	private HeaderName headerName;
	private String value;

	public HeaderField(HeaderName headerName,String value) {
		this.headerName = headerName;
		this.value = value;
	}

	public HeaderField(String line) {
		headerName = new HeaderName(getName(line));
		value = getValue(line);
	}

	private String getValue(String line) {
		return (line.substring(line.indexOf(":")+1)).trim();
	}

	private String getName(String line) {
		return line.substring(0, line.indexOf(":"));
	}

	public HeaderField(String headerName,String value) {
		this(new HeaderName(headerName),value);
	}

	public HeaderField(HeaderField element) {
		headerName = new HeaderName(element.headerName);
		value = element.value;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public HeaderName getHeaderName() {
		return headerName;
	}
	public void setHeaderName(HeaderName headerName) {
		this.headerName = headerName;
	}

	@Override
	public String toString(){
		StringBuilder buf = new StringBuilder();
		buf.append(headerName.toString());
		buf.append(": ");
		buf.append(value);
		buf.append(Constants.CRLF);
		return buf.toString();
	}

	public int estimateHeapSize() {
		return 2*(4 + headerName.toString().length() + value.length());
	}
}
