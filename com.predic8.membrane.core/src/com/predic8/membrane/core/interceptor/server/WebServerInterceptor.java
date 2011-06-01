/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.server;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.*;

public class WebServerInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(WebServerInterceptor.class.getName());
	
	String docBase = "";
	
	public WebServerInterceptor() {
		name = "Web Server";
		priority = 5000;
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String uri = exc.getOriginalRequestUri();
		
		log.debug("request: "+uri);

		log.debug("looking for file: "+getFile(uri).getAbsolutePath());
		
		if (isNotFoundOrDirectory(uri)) {
			exc.setResponse(HttpUtil.createNotFoundResponse());
			return Outcome.ABORT;
		}
		
		exc.setResponse(createResponse(getFile(uri)));
		return Outcome.ABORT;
	}

	private boolean isNotFoundOrDirectory(String uri) {
		return !getFile(uri).exists() || getFile(uri).isDirectory();
	}

	private File getFile(String uri) {
		return FileUtil.prefixMembraneHomeIfNeeded(new File(docBase, uri));
	}

	private Response createResponse(File file) throws Exception {
		Response response = new Response();
		response.setStatusCode(200);
		response.setStatusMessage("OK");
		response.setHeader(createHeader(file));

		response.setBody(new Body(new FileInputStream(file),
								(int) file.length()));
		return response;
	}
	
	private void setContentType(Header h, File file) {
		if (file.getPath().endsWith(".css")) {
			h.add("Content-Type", "text/css");
		} else if (file.getPath().endsWith(".js")) {
			h.add("Content-Type", "application/x-javascript");			
		}
	}

	private Header createHeader(File file) {
		Header header = new Header();
		header.add("Date", HttpUtil.GMT_DATE_FORMAT.format(new Date()));
		header.add("Server", "Membrane-Monitor " + Constants.VERSION);
		header.add("Connection", "close");
		setContentType(header, file);
		return header;
	}

	public String getDocBase() {
		return docBase;
	}

	public void setDocBase(String docBase) {
		this.docBase = docBase;
	}
	

}
