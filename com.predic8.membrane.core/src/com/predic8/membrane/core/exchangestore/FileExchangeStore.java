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
	
	private FileOutputStream outputSream;
		
	private static final DateFormat dateFormat = new SimpleDateFormat("'h'hh'm'mm's'ss'ms'ms");
	
	private static final String fileSeparator = System.getProperty("file.separator");
	
	public void add(Exchange exchange) {
		exchange.getTime().get(Calendar.YEAR);
		String messageName;
		Message message;
		if (exchange.getResponse() == null) {
			messageName = "Request";
			message = exchange.getRequest();
		} else {
			messageName = "Response";
			message = exchange.getResponse();
		}
		
		String fullDirectoryName = dir + fileSeparator + exchange.getTime().get(Calendar.YEAR) + fileSeparator + (exchange.getTime().get(Calendar.MONTH) + 1) + fileSeparator + exchange.getTime().get(Calendar.DAY_OF_MONTH );
		
		directory = new File(fullDirectoryName);
		directory.mkdirs();
		try {
			if (directory.exists() && directory.isDirectory()) {
				writeFile(exchange, messageName, message, fullDirectoryName);
			} else {
				log.error("Directory does not exists or file is not a directory: " + fullDirectoryName);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (outputSream != null) {
					outputSream.close();
				}
			} catch (IOException ex) {
				log.error(ex);
			}
		}
		
	}

	private void writeFile(Exchange exchange, String messageName, Message message, String fullDirectoryName) throws IOException, FileNotFoundException, Exception {
		File file = new File(fullDirectoryName + fileSeparator + getFileName(exchange, messageName));
		if (file.createNewFile()) {
			counter ++;
			outputSream = new FileOutputStream(file);
			message.writeStartLine(outputSream);
			message.getHeader().write(outputSream);
			outputSream.write((Constants.CRLF).getBytes());
			if (raw)
				outputSream.write(message.getBody().getRaw());
			else
				outputSream.write(TextUtil.formatXML(message.getBodyAsStream()).getBytes());
			
		} else {
			log.error("Unable to create file: " + file.getName());
		}
	}
	
	private String getFileName(Exchange exc, String messageName) {
		return dateFormat.format(exc.getTime().getTime()) + "-" + counter + "-" + messageName + ".msg";
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
