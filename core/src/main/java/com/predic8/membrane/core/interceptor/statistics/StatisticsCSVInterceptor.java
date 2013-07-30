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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.util.HtmlUtils;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangesUtil;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

/**
 * @description Writes statistics (time, status code, hostname, URI, etc.) about exchanges passing through into a CSV
 *              file (one line per exchange).
 * @explanation Note that the CSV file is UTF-8 encoded.
 * @topic 5. Monitoring, Logging and Statistics
 */
@MCElement(name="statisticsCSV")
public class StatisticsCSVInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(StatisticsCSVInterceptor.class
			.getName());

	// maps all fileName objects used by instances of this class to themselves.
	// used to get unique String instances of the same file name 
	// (when two instances use the same file name)
	private static final Map<String, String> fileNames = new HashMap<String, String>();
	
	// the file name of the log file; at the same time a lock guarding the lock file
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
		synchronized(fileName) {
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
				w.flush();
			} finally {
				fos.close();
			}
		}
	}

	/**
	 * @description Locations of csv file to write out logs.
	 * @example stat.csv
	 */
	@Required
	@MCAttribute(attributeName="file")
	public void setFileName(String fileName) throws Exception {
		synchronized(fileNames) {
			String fn = fileNames.get(fileName);
			if (fn != null)
				fileName = fn;
			else
				fileNames.put(fileName, fileName);
			this.fileName = fileName;
			createCSVFile();
		}
	}

	private void createCSVFile() throws Exception {
		synchronized (fileName) {
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
		synchronized(fileName) {
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
				w.flush();
			} finally {
				fos.close();
			}
		}
	}

	@Override
	public String getShortDescription() {
		return "Logs access statistics into the CSV-based file " + HtmlUtils.htmlEscape(fileName) + " .";
	}
	
}
