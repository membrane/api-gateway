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
package com.predic8.membrane.core.interceptor.statistics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangesUtil;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class StatisticsCSVInterceptor extends AbstractInterceptor {

	private FileOutputStream out;

	private StringBuffer buf = new StringBuffer();

	public StatisticsCSVInterceptor() {
		priority = 510;
	}
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		writeExchange(exc);
		return Outcome.CONTINUE;
	}

	private void writeExchange(Exchange exc) throws Exception {
		buf.append(ExchangesUtil.getStatusCode(exc));
		buf.append(";");
		buf.append(ExchangesUtil.getTime(exc));
		buf.append(";");
		buf.append(exc.getRule().toString());
		buf.append(";");
		buf.append(exc.getRequest().getMethod());
		buf.append(";");
		buf.append(exc.getRequest().getUri());
		buf.append(";");
		buf.append(exc.getSourceHostname());
		buf.append(";");
		buf.append(exc.getServer());
		buf.append(";");
		buf.append(exc.getRequestContentType());
		buf.append(";");
		buf.append(ExchangesUtil.getRequestContentLength(exc));
		buf.append(";");
		buf.append(ExchangesUtil.getResponseContentType(exc));
		buf.append(";");
		buf.append(ExchangesUtil.getResponseContentLength(exc));
		buf.append(";");
		buf.append(ExchangesUtil.getTimeDifference(exc));
		buf.append(";");
		buf.append(System.getProperty("line.separator"));
		flushBuffer();
	}

	private void flushBuffer() throws Exception {
		out.write(buf.toString().getBytes());
		out.flush();
		buf.setLength(0);
	}

	public void setFileName(String fileName) throws Exception {
		File file = new File(fileName);
		if (!file.exists())
			file.createNewFile();
		if (!file.canWrite())
			throw new IOException("File " + fileName + " is not writable.");
		out = new FileOutputStream(file, true);
		if (file.length() == 0)
			writeHeaders();
	}

	private void writeHeaders() throws Exception {
		buf.append("Status Code");
		buf.append(";");
		buf.append("Time");
		buf.append(";");
		buf.append("Rule");
		buf.append(";");
		buf.append("Method");
		buf.append(";");
		buf.append("Path");
		buf.append(";");
		buf.append("Client");
		buf.append(";");
		buf.append("Server");
		buf.append(";");
		buf.append("Request Content-Type");
		buf.append(";");
		buf.append("Request Content Length");
		buf.append(";");
		buf.append("Response Content-Type");
		buf.append(";");
		buf.append("Response Content Length");
		buf.append(";");
		buf.append("Duration");
		buf.append(";");
		buf.append(System.getProperty("line.separator"));
		flushBuffer();
	}
}
