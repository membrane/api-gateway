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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import org.apache.commons.text.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * @description Writes statistics (time, status code, hostname, URI, etc.) about exchanges passing through into a CSV
 *              file (one line per exchange).
 * @explanation Note that the CSV file is UTF-8 encoded.
 * @topic 5. Monitoring, Logging and Statistics
 */
@MCElement(name="statisticsCSV")
public class StatisticsCSVInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(StatisticsCSVInterceptor.class
			.getName());

	// maps all fileName objects used by instances of this class to themselves.
	// used to get unique String instances of the same file name
	// (when two instances use the same file name)
	private static final Map<String, String> fileNames = new HashMap<>();

	// the file name of the log file; at the same time a lock guarding the lock file
	private String fileName;

	public StatisticsCSVInterceptor() {
		name = "CSV Logging";
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		log.debug("logging statistics to " + new File(fileName).getAbsolutePath());
		writeExchange(exc);
		return CONTINUE;
	}

	private void writeExchange(Exchange exc) throws Exception {
		synchronized(fileName) {
			try(FileOutputStream fos = new FileOutputStream(fileName, true)) {
				OutputStreamWriter w = new OutputStreamWriter(fos, UTF_8);

				writeCSV(ExchangesUtil.getStatusCode(exc), w);
				writeCSV(ExchangesUtil.getTime(exc), w);
				writeCSV(exc.getRule().toString(), w);
				writeCSV(exc.getRequest().getMethod(), w);
				writeCSV(exc.getRequest().getUri(), w);
				writeCSV(exc.getRemoteAddr(), w);
				writeCSV(exc.getServer(), w);
				writeCSV(exc.getRequestContentType(), w);
				writeCSV(ExchangesUtil.getRequestContentLength(exc), w);
				writeCSV(ExchangesUtil.getResponseContentType(exc), w);
				writeCSV(ExchangesUtil.getResponseContentLength(exc), w);
				writeCSV(ExchangesUtil.getTimeDifference(exc), w);
				writeNewLine(w);
				w.flush();
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

				//noinspection ResultOfMethodCallIgnored
				csvFile.getParentFile().mkdirs();
			}

			//noinspection ResultOfMethodCallIgnored
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
		w.append(value).append(";");
	}

	private void writeNewLine(OutputStreamWriter w) throws IOException {
		w.append(System.getProperty("line.separator"));
	}

	private void writeHeaders() throws Exception {
		synchronized(fileName) {
			try(FileOutputStream fos = new FileOutputStream(fileName, true)) {
				OutputStreamWriter w = new OutputStreamWriter(fos, UTF_8);

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
			}
		}
	}

	@Override
	public String getShortDescription() {
		return "Logs access statistics into the CSV-based file " + StringEscapeUtils.escapeHtml4(fileName) + " .";
	}
}
