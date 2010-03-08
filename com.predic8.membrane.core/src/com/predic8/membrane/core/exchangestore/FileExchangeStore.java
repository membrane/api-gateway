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

package com.predic8.membrane.core.exchangestore;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.statistics.RuleStatistics;
import com.predic8.membrane.core.util.TextUtil;

public class FileExchangeStore extends AbstractExchangeStore {

	private static Log log = LogFactory.getLog(FileExchangeStore.class.getName());

	private String dir;

	private boolean raw;

	private File directory;

	private static int counter = 0;

	private static final DateFormat dateFormat = new SimpleDateFormat("'h'hh'm'mm's'ss'ms'ms");

	private static final String separator = System.getProperty("file.separator");

	public void add(Exchange exc) {
		exc.getTime().get(Calendar.YEAR);
		String messageName = exc.getResponse() == null ? "Request" : "Response";
		Message message = exc.getResponse() == null ? exc.getRequest() : exc.getResponse();

		String fullDirectoryName = getFullDirectoryName(exc);

		directory = new File(fullDirectoryName);
		directory.mkdirs();
		try {
			if (directory.exists() && directory.isDirectory()) {
				writeFile(exc, messageName, message, fullDirectoryName);
			} else {
				log.error("Directory does not exists or file is not a directory: " + fullDirectoryName);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private String getFullDirectoryName(Exchange exc) {
		return dir + separator + exc.getTime().get(Calendar.YEAR) + separator + (exc.getTime().get(Calendar.MONTH) + 1) + separator + exc.getTime().get(Calendar.DAY_OF_MONTH);
	}

	private void writeFile(Exchange exc, String messageName, Message msg, String fullDirectoryName) throws IOException, FileNotFoundException, Exception {
		File file = new File(fullDirectoryName + separator + getFileName(exc, messageName));
		if (!file.createNewFile()) {
			log.error("Unable to create file: " + file.getName());
			return;
		}

		FileOutputStream os = new FileOutputStream(file);
		try {
			msg.writeStartLine(os);
			msg.getHeader().write(os);
			os.write((Constants.CRLF).getBytes());
			if (!msg.isBodyEmpty()) {
				if (raw)
					os.write(msg.getBody().getRaw());
				else {
					os.write(TextUtil.formatXML(msg.getBodyAsStream()).getBytes());
				}
			}
		} catch (Exception e) {
			throw e;
		} finally {
			os.close();
		}
	}

	/**
	 * Must be synchronized.
	 * 
	 * @param exc
	 * @param messageName
	 * @return
	 */
	synchronized private String getFileName(Exchange exc, String messageName) {
		return dateFormat.format(exc.getTime().getTime()) + "-" + counter++ + "-" + messageName + ".msg";
	}

	public Exchange[] getExchanges(RuleKey ruleKey) {
		throw new RuntimeException("Method getExchanges() is not supported by FileExchangeStore");
	}

	public int getNumberOfExchanges(RuleKey ruleKey) {
		throw new RuntimeException("Method getNumberOfExchanges() is not supported by FileExchangeStore");
	}

	public void remove(Exchange exchange) {
		throw new RuntimeException("Method remove() is not supported by FileExchangeStore");
	}

	public void removeAllExchanges(Rule rule) {
		throw new RuntimeException("Method removeAllExchanges() is not supported by FileExchangeStore");
	}

	public String getDir() {
		return dir;
	}

	public void setDir(String dir) {
		this.dir = dir;
	}

	public boolean isRaw() {
		return raw;
	}

	public void setRaw(boolean raw) {
		this.raw = raw;
	}

	public RuleStatistics getStatistics(RuleKey ruleKey) {

		return null;
	}

	public Object[] getAllExchanges() {
		// TODO Auto-generated method stub
		return null;
	}

	public Object[] getLatExchanges(int count) {
		// TODO Auto-generated method stub
		return null;
	}

	public List<Exchange> getAllExchangesAsList() {
		// TODO Auto-generated method stub
		return null;
	}

	public void removeAllExchanges(Exchange[] exchanges) {
		// TODO Auto-generated method stub

	}

}
