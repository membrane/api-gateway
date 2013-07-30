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

package com.predic8.membrane.core.resolver;

import java.io.IOException;

public class ResourceRetrievalException extends IOException {
	private static final long serialVersionUID = 1L;

	private int status;
	private String url;

	public ResourceRetrievalException(String url) {
		super();
		this.url = url;
	}

	public ResourceRetrievalException(String url, int statusCode) {
		super();
		this.url = url;
		this.status = statusCode;
	}

	public ResourceRetrievalException(String url, Exception e) {
		super(e);
		this.url = url;
	}
	
	@Override
	public String getMessage() {
		return super.getMessage() + (status == 0 ? "" : " returned status " + status) + " while retrieving " + url;
	}
	
	public int getStatus() {
		return status;
	}
}