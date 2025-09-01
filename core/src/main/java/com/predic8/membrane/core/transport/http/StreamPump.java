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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.model.*;
import com.predic8.membrane.core.proxies.Proxy;
import org.slf4j.*;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.transport.http.client.protocol.WebSocketProtocolHandler.*;

public class StreamPump implements Runnable {

	protected static final Logger log = LoggerFactory.getLogger(StreamPump.class.getName());

	public static class StreamPumpStats {
		private static final ArrayList<StreamPump> pumps = new ArrayList<>();

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
			return new ArrayList<>(pumps);
		}
		public synchronized void closeAllStreamPumps() {
			for (StreamPump p : new ArrayList<>(pumps)) {
				p.close();
			}
		}
	}

	// operational members
	protected final InputStream in;
	protected final OutputStream out;
	protected final StreamPumpStats stats;
	// statistical members
	protected final AtomicLong bytesTransferred;
	private final String pumpName;
	private final long creationTime;
	private final Proxy proxy;

	public StreamPump(InputStream in, OutputStream out, StreamPumpStats stats, String name, Proxy proxy) {
		this.in = in;
		this.out = out;
		this.stats = stats;
		this.bytesTransferred = new AtomicLong();
		this.pumpName = name;
		this.creationTime = System.currentTimeMillis();
		this.proxy = proxy;
	}

	@Override
	public void run() {
		byte[] buffer = new byte[8192];
		int length;
		if (stats != null)
			stats.registerPump(this);
		try {
			while ((length = in.read(buffer)) > 0) {
				out.write(buffer, 0, length);
				out.flush();
				if (stats != null)
					bytesTransferred.addAndGet(length);
			}
		} catch (SocketTimeoutException | SSLException | SocketException e) {
			// do nothing
		} catch (IOException e) {
			log.error("Reading from or writing to stream failed: {}", e.getMessage());
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
		return proxy.getName();
	}
	public synchronized long getTransferredBytes() {
		return bytesTransferred.get();
	}
	public synchronized long getCreationTime() {
		return creationTime;
	}

	public synchronized void close() {
		try {
			log.debug("Closing Stream Pump {}",pumpName);
			in.close();
			out.close();
		} catch (IOException e) {
			log.error("While closing stream pump.", e);
		}
	}

	/**
	 * For proxy
	 * @param exc
	 * @param con
	 * @param protocol
	 * @param stats
	 * @throws SocketException
	 */
	public static void setupConnectionForwarding(Exchange exc, final Connection con, final String protocol, StreamPump.StreamPumpStats stats) throws SocketException {
		final TwoWayStreaming tws = (TwoWayStreaming) exc.getHandler();
		String source = tws.getRemoteDescription();
		String dest = con.toString();
		final StreamPump a;
		final StreamPump b;
		if (WEBSOCKET.equalsIgnoreCase(protocol)) {
			WebSocketStreamPump aTemp = new WebSocketStreamPump(tws.getSrcIn(), con.out, stats, protocol + " " + source + " -> " + dest, exc.getProxy(), true, exc);
			WebSocketStreamPump bTemp = new WebSocketStreamPump(con.in, tws.getSrcOut(), stats, protocol + " " + source + " <- " + dest, exc.getProxy(), false, null);
			aTemp.init(bTemp);
			bTemp.init(aTemp);
			a = aTemp;
			b = bTemp;
		} else {
			a = new StreamPump(tws.getSrcIn(), con.out, stats, protocol + " " + source + " -> " + dest, exc.getProxy());
			b = new StreamPump(con.in, tws.getSrcOut(), stats, protocol + " " + source + " <- " + dest, exc.getProxy());
		}

		tws.removeSocketSoTimeout();

		exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {

			@Override
			public void setExchangeFinished() {
				runClient(log, b, protocol, a, con);
			}
		});
	}

	public static void runClient(Logger log, StreamPump b, String protocol, StreamPump pump, Connection con) {
		String threadName = Thread.currentThread().getName();

		new Thread(b,  "%s %s Backward Thread".formatted( threadName, protocol)).start();
		try {
			Thread.currentThread().setName("%s %s Onward Thread".formatted( threadName, protocol));
			pump.run();
		} finally {
			try {
				con.close();
			} catch (IOException e) {
				log.debug("", e);
			}
		}
	}

}
