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

import java.io.*;

import javax.xml.stream.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;

/**
 * The output file is UTF-8 encoded.
 */
public class StatisticsCSVInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(StatisticsCSVInterceptor.class
			.getName());

	// private File csvFile;
	private String fileName;

	public StatisticsCSVInterceptor() {
		name = "CSV Logging";
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		log.debug("logging statistics to "
				+ new File(fileName).getAbsolutePath());
		writeExchange(exc);
		return Outcome.CONTINUE;
	}

	private void writeExchange(Exchange exc) throws Exception {
		FileOutputStream fos = new FileOutputStream(fileName, true);
		try {
			OutputStreamWriter w = new OutputStreamWriter(fos, Constants.UTF_8_CHARSET);

			writeCSV(ExchangesUtil.getStatusCode(exc), w);
			writeCSV(ExchangesUtil.getTime(exc), w);
			writeCSV(exc.getRule().toString(), w);
			writeCSV(exc.getRequest().getMethod(), w);
			writeCSV(exc.getRequest().getUri(), w);
			writeCSV(exc.getSourceHostname(), w);
			writeCSV(exc.getServer(), w);
			writeCSV(exc.getRequestContentType(), w);
			writeCSV(ExchangesUtil.getRequestContentLength(exc), w);
			writeCSV(ExchangesUtil.getResponseContentType(exc), w);
			writeCSV(ExchangesUtil.getResponseContentLength(exc), w);
			writeCSV(ExchangesUtil.getTimeDifference(exc), w);
			writeNewLine(w);
		} finally {
			fos.close();
		}
	}

	public void setFileName(String fileName) throws Exception {
		this.fileName = fileName;
		createCSVFile();
	}

	private void createCSVFile() throws Exception {
		File csvFile = new File(fileName);
		log.debug("creating csv file at " + csvFile.getAbsolutePath());

		if (csvFile.getParentFile() != null) {
			csvFile.getParentFile().mkdirs();
		}
		csvFile.createNewFile();

		if (!csvFile.canWrite())
			throw new IOException("File " + fileName + " is not writable.");

		if (csvFile.length() == 0)
			writeHeaders();
	}

	public String getFileName() {
		return new File(fileName).getName();
	}

	private void writeCSV(String value, OutputStreamWriter w) throws IOException {
		w.append(value + ";");
	}

	private void writeNewLine(OutputStreamWriter w) throws IOException {
		w.append(System.getProperty("line.separator"));
	}

	private void writeHeaders() throws Exception {
		FileOutputStream fos = new FileOutputStream(fileName, true);
		try {
			OutputStreamWriter w = new OutputStreamWriter(fos, Constants.UTF_8_CHARSET);

			writeCSV("Status Code", w);
			writeCSV("Time", w);
			writeCSV("Rule", w);
			writeCSV("Method", w);
			writeCSV("Path", w);
			writeCSV("Client", w);
			writeCSV("Server", w);
			writeCSV("Request Content-Type", w);
			writeCSV("Request Content Length", w);
			writeCSV("Response Content-Type", w);
			writeCSV("Response Content Length", w);
			writeCSV("Duration", w);
			writeNewLine(w);
		} finally {
			fos.close();
		}
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("statisticsCSV");
		out.writeAttribute("file", fileName);

		out.writeEndElement();
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		setFileName(token.getAttributeValue("", "file"));
	}
}
