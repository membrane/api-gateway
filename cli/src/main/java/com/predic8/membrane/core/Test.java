/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. */

package com.predic8.membrane.core;

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.util.URIFactory;

public class Test {
	public static void main(String[] args) throws Exception {
		
		URIFactory uriFactory = new URIFactory();
		uriFactory.setAllowIllegalCharacters(true);
		Response res = new HttpClient().call(new Request.Builder().get(uriFactory, "http://localhost:2000/a.{/").buildExchange()).getResponse();
		System.out.println(res.getStatusCode());
		System.out.println(res.getStartLine());
	}
}
