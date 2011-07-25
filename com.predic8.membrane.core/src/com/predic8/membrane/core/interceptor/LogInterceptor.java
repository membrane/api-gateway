/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;

public class LogInterceptor extends AbstractInterceptor {

	private Writer writer;
	
	public LogInterceptor() {
		writer = new BufferedWriter(new OutputStreamWriter(System.out));
	}
	
	public Outcome handleRequest(AbstractExchange exchange) throws Exception {
		printRequest(exchange);
		writer.write("\n");
		writer.flush();
		return Outcome.CONTINUE;
	}

	private void printRequest(AbstractExchange exchange) throws IOException {
		Request request = exchange.getRequest();
		writer.write("==== Request ===\n");
		if (request == null) {
			writer.write(" !!! Request object is null !!! \n");
		} else {
			writer.write(request.toString() + "\n");
		}
		writer.write("================\n");
	}
	
	private void printResponse(AbstractExchange exchange) throws IOException {
		Response response = exchange.getResponse();
		writer.write("==== Response ===\n");
		if (response == null) {
			writer.write(" !!! Response object is null !!! \n");
		} else {
			writer.write(response.toString() + "\n");
		}
		writer.write("================\n");
	}
	
	@Override
	public Outcome handleResponse(Exchange exchange) throws Exception {
		printResponse(exchange);
		writer.write("\n");
		writer.flush();
		return Outcome.CONTINUE;
	}
}

