package com.predic8.membrane.core.interceptor.statistics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangesUtil;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class CSVStatisticsInterceptor extends AbstractInterceptor {

	private String fileName = "";

	private File file;

	private boolean canWrite;

	private StringBuffer buf = new StringBuffer();

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		buf.delete(0, buf.length());
		writeExchangeToBuffer(exc);
		writeBufferContentToOutput();
		buf.delete(0, buf.length());
		return Outcome.CONTINUE;
	}

	private void writeExchangeToBuffer(Exchange exc) {
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
	}

	private void writeBufferContentToOutput() throws Exception, IOException {
		if (!canWrite) 
			return;
		
		OutputStream out = null;
		try {
			out = new FileOutputStream(file, true);
			out.write(buf.toString().getBytes());
		} catch (Exception e) {
			throw e;
		} finally {
			buf.delete(0, buf.length());
			if (out != null)
				out.close();
		}
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
		createFile();
		writeHeadersToBuffer();
		try {
			writeBufferContentToOutput();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void createFile() {
		file = new File(fileName);
		if (!file.exists()) {
			try {
				canWrite = file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			canWrite = true;
		}
	}

	private void writeHeadersToBuffer() {
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
	}
}
