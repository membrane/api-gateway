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

package com.predic8.membrane.core.exchangestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.MessageObserver;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.rules.StatisticCollector;
import com.predic8.membrane.core.util.TextUtil;

/**
 * The output file is UTF-8 encoded.
 */
@MCElement(name="fileExchangeStore")
public class FileExchangeStore extends AbstractExchangeStore {

	private static Log log = LogFactory.getLog(FileExchangeStore.class
			.getName());

	private static AtomicInteger counter = new AtomicInteger();

	private static final String DATE_FORMAT = "'h'HH'm'mm's'ss'ms'SSS"; 
	
	private static final ThreadLocal<DateFormat> dateFormat = new ThreadLocal<DateFormat>();

	private static final String separator = System
			.getProperty("file.separator");

	public static final String MESSAGE_FILE_PATH = "message.file.path";

	private String dir;

	private boolean raw;

	private File directory;

	private boolean saveBodyOnly = false;

	private int maxDays;

	public void snap(final AbstractExchange exc, final Flow flow) {
		try {
			Message m = flow == Flow.REQUEST ? exc.getRequest() : exc.getResponse();
			// TODO: [fix me] support multi-snap
			// TODO: [fix me] snap message headers *here*, not in observer 
			if (m != null)
				m.addObserver(new MessageObserver() {
					public void bodyRequested(AbstractBody body) {
					}
					public void bodyComplete(AbstractBody body) {
						snapInternal(exc, flow);
					}
				});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
		
	private void snapInternal(AbstractExchange exc, Flow flow) {
		int fileNumber = counter.incrementAndGet();

		StringBuilder buf = getDirectoryNameBuffer(exc.getTime());

		directory = new File(buf.toString());
		directory.mkdirs();
		if (directory.exists() && directory.isDirectory()) {
			buf.append(separator);
			buf.append(getDateFormat().format(exc.getTime().getTime()));
			buf.append("-");
			buf.append(fileNumber);
			exc.setProperty(MESSAGE_FILE_PATH, buf.toString());
			buf.append("-");
			StringBuilder buf2 = new StringBuilder(buf);
			buf.append("Request.msg");
			buf2.append("Response.msg");
			try {
				switch (flow) {
				case REQUEST:
					writeFile(exc.getRequest(), buf.toString());
					break;
				case ABORT:
				case RESPONSE:
					if (exc.getResponse() != null)
						writeFile(exc.getResponse(), buf2.toString());
				}
			} catch (Exception e) {
				log.error(e, e);
			}
		} else {
			log.error("Directory does not exists or file is not a directory: "+ buf.toString());
		}

	}

	private StringBuilder getDirectoryNameBuffer(Calendar time) {
		StringBuilder buf = new StringBuilder();
		buf.append(dir);
		buf.append(separator);
		buf.append(time.get(Calendar.YEAR));
		buf.append(separator);
		buf.append((time.get(Calendar.MONTH) + 1));
		buf.append(separator);
		buf.append(time.get(Calendar.DAY_OF_MONTH));
		return buf;
	}
	
	private static DateFormat getDateFormat() {
		DateFormat df = dateFormat.get();
		if (df == null) {
			df = new SimpleDateFormat(DATE_FORMAT);
			dateFormat.set(df);
		}
		return df;
	}

	private void writeFile(Message msg, String path) throws Exception {
		File file = new File(path);
		file.createNewFile();

		FileOutputStream os = new FileOutputStream(file);
		try {
			if (raw || !saveBodyOnly) {
				msg.writeStartLine(os);
				msg.getHeader().write(os);
				os.write(Constants.CRLF_BYTES);
			}

			if (msg.isBodyEmpty())
				return;

			if (raw)
				os.write(msg.getBody().getRaw());
			else {
				if (msg.isXML())
					os.write(TextUtil.formatXML(
							new InputStreamReader(msg.getBodyAsStream(), msg
									.getHeader().getCharset()))
							.getBytes(Constants.UTF_8));
				else
					os.write(msg.getBody().getContent());
			}
		} finally {
			os.close();
		}
	}

	public void deleteOldFolders() throws IOException {
		Calendar threshold = Calendar.getInstance();
		threshold.add(Calendar.DAY_OF_MONTH, -maxDays);

		ArrayList<File> folders3 = new DepthWalker(3).getDirectories(new File(dir));

		ArrayList<File> deletion = new ArrayList<File>();

		for (File f : folders3) {
			System.out.println(f);
			int day =  Integer.parseInt(f.getName());
			int mon =  Integer.parseInt(f.getParentFile().getName());
			int year = Integer.parseInt(f.getParentFile().getParentFile().getName());
			Calendar folderTime = Calendar.getInstance();
			folderTime.clear();
			folderTime.set(year, mon-1, day);
			if (folderTime.before(threshold)) {
				deletion.add(f);
			}
		}

		for (File d : deletion) {
			//System.out.println(d + " would be deleted");
			d.delete();
		}
	}

	public AbstractExchange[] getExchanges(RuleKey ruleKey) {
		throw new RuntimeException(
				"Method getExchanges() is not supported by FileExchangeStore");
	}

	public int getNumberOfExchanges(RuleKey ruleKey) {
		throw new RuntimeException(
				"Method getNumberOfExchanges() is not supported by FileExchangeStore");
	}

	public void remove(AbstractExchange exchange) {
		throw new RuntimeException(
				"Method remove() is not supported by FileExchangeStore");
	}

	public void removeAllExchanges(Rule rule) {
		throw new RuntimeException(
				"Method removeAllExchanges() is not supported by FileExchangeStore");
	}

	public String getDir() {
		return dir;
	}

	/**
	 * @description Directory where the exchanges are saved.
	 * @example logs
	 */
	@Required
	@MCAttribute
	public void setDir(String dir) {
		this.dir = dir;
	}

	public boolean isRaw() {
		return raw;
	}

	/**
	 * @default false
	 * @example true
	 */
	@MCAttribute
	public void setRaw(boolean raw) {
		this.raw = raw;
	}

	public StatisticCollector getStatistics(RuleKey ruleKey) {
		return null;
	}

	public Object[] getAllExchanges() {
		return null;
	}

	public Object[] getLatExchanges(int count) {
		return null;
	}

	public List<AbstractExchange> getAllExchangesAsList() {
		return null;
	}

	public void removeAllExchanges(AbstractExchange[] exchanges) {
		// ignore
	}

	public boolean isSaveBodyOnly() {
		return saveBodyOnly;
	}

	/**
	 * @default false
	 * @example true
	 */
	@MCAttribute
	public void setSaveBodyOnly(boolean saveBodyOnly) {
		this.saveBodyOnly = saveBodyOnly;
	}

	public int getMaxDays() {
		return maxDays;
	}

	/**
	 * @default not set
	 * @example 60
	 */
	@MCAttribute
	public void setMaxDays(int maxDays) {
		this.maxDays = maxDays;
	}

}
