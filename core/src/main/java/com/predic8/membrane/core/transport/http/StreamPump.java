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
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.rules.Rule;

public class StreamPump implements Runnable {

	private static Log log = LogFactory.getLog(StreamPump.class.getName());

	public static class StreamPumpStats {
		private static ArrayList<StreamPump> pumps = new ArrayList<StreamPump>();

		public synchronized int getRunning() {
			return pumps.size();
		}
		public synchronized void registerPump(StreamPump pump) {
			pumps.add(pump);
		}
		public synchronized void unregisterPump(StreamPump pump) {
			pumps.remove(pump);
		}
		public synchronized List<StreamPump> getStreamPumps() {
			return new ArrayList<StreamPump>(pumps);
		}
		public synchronized void closeAllStreamPumps() {
			for (StreamPump p : pumps) {
				p.close();
			}
		}
	}

	// operational members
	private final InputStream in;
	private final OutputStream out;
	private StreamPumpStats stats;
	// statistical members
	private AtomicLong bytesTransferred;
	private String pumpName;
	private final long creationTime;
	private Rule rule;

	public StreamPump(InputStream in, OutputStream out, StreamPumpStats stats, String name, Rule rule) {
		this.in = in;
		this.out = out;
		this.stats = stats;
		this.bytesTransferred = new AtomicLong();
		this.pumpName = name;
		this.creationTime = System.currentTimeMillis();
		this.rule = rule;
	}

	@Override
	public void run() {
		byte[] buffer = new byte[8192];
		int length = 0;
		if (stats != null)
			stats.registerPump(this);
		try {
			while ((length = in.read(buffer)) > 0) {
				out.write(buffer, 0, length);
				out.flush();
				if (stats != null)
					bytesTransferred.addAndGet(length);
			}
		} catch (SocketTimeoutException e) {
			// do nothing
		} catch (SocketException e) {
			// do nothing
		} catch (IOException e) {
			log.error("Reading from or writing to stream failed: " + e);
		} finally {
			try {
				out.close();
			} catch (Exception e) {
				// ignore
			}
			if (stats != null)
				stats.unregisterPump(this);
		}
	}

	public String getName() {
		return this.pumpName;
	}
	public String getServiceProxyName() {
		return rule.getName();
	}
	public synchronized long getTransferredBytes() {
		return bytesTransferred.get();
	}
	public synchronized long getCreationTime() {
		return creationTime;
	}

	public synchronized void close() {
		try {
			log.debug("Closing Stream Pump '" + pumpName + "'");
			in.close();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
