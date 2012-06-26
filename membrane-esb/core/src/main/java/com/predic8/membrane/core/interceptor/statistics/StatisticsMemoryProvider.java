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
package com.predic8.membrane.core.interceptor.statistics;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangesUtil;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil;
import com.predic8.membrane.core.util.URLParamUtil;

public class StatisticsMemoryProvider extends AbstractInterceptor {
	private static Log log = LogFactory.getLog(StatisticsMemoryProvider.class
			.getName());

	private final JsonFactory jsonFactory = new JsonFactory(); // thread-safe
																// after
																// configuration
	public StatisticsMemoryProvider() {
		name = "Provides caller statistics as JSON";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		try {
			int offset = URLParamUtil.getIntParam(exc, "offset");
			int max = URLParamUtil.getIntParam(exc, "max");
			List<AbstractExchange> exchanges = getRouter().getExchangeStore().getAllExchangesAsList(); 
			createJson(exc, exchanges.subList(offset, Math.min(offset+max, exchanges.size())), exchanges.size());
		} catch (Exception e) {
			e.printStackTrace();
			log.warn("Could not retrieve statistics.", e);
			return Outcome.ABORT;
		} 

		return Outcome.RETURN;
	}

	private void createResponse(Exchange exc, StringWriter jsonTxt) {
		exc.setResponse(Response.ok()
				.body(jsonTxt.toString()).build());
	}

	private void createJson(Exchange exc, List<AbstractExchange> list, int total) throws IOException,
			JsonGenerationException, SQLException {

		StringWriter jsonTxt = new StringWriter();

		JsonGenerator jsonGen = jsonFactory.createJsonGenerator(jsonTxt);
		jsonGen.writeStartObject();
			jsonGen.writeArrayFieldStart("statistics");
				for (AbstractExchange e : list) {
					writeRecord(e, jsonGen);
				}
			jsonGen.writeEndArray();
			jsonGen.writeNumberField("total", total);
		jsonGen.writeEndObject();
		jsonGen.flush();

		createResponse(exc, jsonTxt);
	}

	private void writeRecord(AbstractExchange exc, JsonGenerator jsonGen)
			throws IOException, JsonGenerationException, SQLException {
		jsonGen.writeStartObject();
		jsonGen.writeNumberField("statusCode", exc.getResponse().getStatusCode());
		jsonGen.writeStringField("time", ExchangesUtil.getTime(exc));
		jsonGen.writeStringField("rule", exc.getRule().toString());
		jsonGen.writeStringField("method", exc.getRequest().getMethod());
		jsonGen.writeStringField("path", exc.getRequest().getUri());
		jsonGen.writeStringField("client", exc.getSourceHostname());
		jsonGen.writeStringField("server", exc.getServer());
		jsonGen.writeStringField("reqContentType",
				exc.getRequestContentType());
		jsonGen.writeNumberField("reqContentLenght",
				exc.getRequestContentLength());
		jsonGen.writeStringField("respContentType",
				exc.getResponseContentType());
		jsonGen.writeNumberField("respContentLenght",
				exc.getResponseContentLength());
		jsonGen.writeNumberField("duration", exc.getTimeResReceived() - exc.getTimeReqSent());
		jsonGen.writeStringField("msgFilePath",JDBCUtil.getFilePath(exc));
		jsonGen.writeEndObject();
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("statisticsMemoryProvider");

		out.writeEndElement();
	}

}
