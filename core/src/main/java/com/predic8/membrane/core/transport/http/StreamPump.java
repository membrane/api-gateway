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

package com.predic8.membrane.core.transport.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class StreamPump implements Runnable {

	private static Log log = LogFactory.getLog(StreamPump.class.getName());

	private final InputStream in;
	private final OutputStream out;
	
	public StreamPump(InputStream in, OutputStream out) {
		this.in = in;
		this.out = out;
	}
	
	@Override
	public void run() {
		byte[] buffer = new byte[8192];
		int length = 0;
		try {
			while ((length = in.read(buffer)) > 0) {
				//log.debug(Thread.currentThread().getName() + " pumped " + length + " bytes.");
				out.write(buffer, 0, length);
				out.flush();
			}
		} catch (SocketTimeoutException e) {
			// do nothing
		} catch (SocketException e) {
			// do nothing
		} catch (IOException e) {
			log.error("Reading from or writing to stream failed: " + e);
		}
		//log.debug(Thread.currentThread().getName() + " done.");
	}
}
