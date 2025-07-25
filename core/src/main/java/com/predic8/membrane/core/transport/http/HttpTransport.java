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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.model.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.*;
import com.predic8.membrane.core.transport.ssl.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import javax.annotation.*;
import java.io.*;
import java.lang.ref.*;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

import static com.google.common.base.Objects.*;
import static java.lang.Integer.*;
import static java.lang.String.*;
import static java.util.concurrent.TimeUnit.*;

/**
 * @description <p>
 *              The transport receives messages from clients and invokes interceptors in the request and response flow.
 *              The interceptors that are engaged with the transport are global and are invoked for each message flowing
 *              through the router.
 *              </p>
 */
@MCElement(name="transport")
public class HttpTransport extends Transport {

	private static final Logger log = LoggerFactory.getLogger(HttpTransport.class.getName());

	private int socketTimeout = 30000;
	private int forceSocketCloseOnHotDeployAfter = 30000;
	private boolean tcpNoDelay = true;
	private int backlog = 50;

	private final Map<Integer, Map<IpPort, HttpEndpointListener>> portListenerMapping = new HashMap<>();
	private final List<WeakReference<HttpEndpointListener>> stillRunning = new ArrayList<>();

	private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

	@Override
	public void init(Router router) throws Exception {
		super.init(router);
	}

	/**
	 * Closes the corresponding server port. Note that connections might still be open and exchanges still running after
	 * this method completes.
	 */
	public synchronized void closePort(IpPort p) {
	    Map<IpPort, HttpEndpointListener> mih = portListenerMapping.get(p.port());
	    if (mih == null || mih.isEmpty()) {
	        return;
	    }
		HttpEndpointListener plt = mih.get(p);
		if (plt == null)
			return;
		log.info("Closing server port: {}", p);

		try {
			plt.closePort();
			plt.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
            log.error("Error closing server port: {}", p.port());
        }
        mih.remove(p);
		if (mih.isEmpty()) {
		    portListenerMapping.remove(p.port());
		}
		stillRunning.add(new WeakReference<>(plt));

		for (IPortChangeListener listener : menuListeners) {
			listener.removePort(p.port());
		}
	}

	@Override
	public synchronized void closeAll(boolean waitForCompletion) {
		log.debug("Closing all network server sockets.");
		List<IpPort> all = new ArrayList<>();
		for (Map<IpPort, HttpEndpointListener> v : portListenerMapping.values()) {
		    all.addAll(v.keySet());
		}
		for (IpPort ipPort : all) { // don't iterate thru portListenerMapping !!!
			closePort(ipPort);
		}
		log.debug("Closing all stream pumps.");
		Router router = getRouter();
		if (router != null)
			router.getStatistics().getStreamPumpStats().closeAllStreamPumps();

		if (waitForCompletion) {
			long now = System.currentTimeMillis();
			log.debug("Waiting for running exchanges to finish.");
			executorService.shutdown();
			try {
				while (true) {
                    closeConnections(closeOnlyIdleConnections(now));
					if (executorService.awaitTermination(5, SECONDS))
						break;
					log.warn("Still waiting for running exchanges to finish. (Set <transport forceSocketCloseOnHotDeployAfter=\"{}\"> to a lower value to forcibly close connections more quickly.",forceSocketCloseOnHotDeployAfter);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/*
	 * Close all connections after some time
	 */
	private boolean closeOnlyIdleConnections(long now) {
		return System.currentTimeMillis() - now <= forceSocketCloseOnHotDeployAfter;
	}

	private void closeConnections(boolean onlyIdle) {
		ArrayList<WeakReference<HttpEndpointListener>> remove = new ArrayList<>();
		for (WeakReference<HttpEndpointListener> whel : stillRunning) {
			HttpEndpointListener hel = whel.get();
			if (hel == null)
				remove.add(whel);
			else
				if (hel.closeConnections(onlyIdle))
					remove.add(whel);
		}
		for (WeakReference<HttpEndpointListener> whel : remove)
			stillRunning.remove(whel);
	}

	/**
	 * @param port Port to open
	 * @param timerManager timerManager
	 * @throws IOException If port can not be opened
	 */
	@Override
	public synchronized void openPort(String ip, int port, SSLProvider sslProvider, @Nullable TimerManager timerManager) throws IOException {
	    if (port == -1)
			throw new RuntimeException("The port-attribute is missing (probably on a <serviceProxy> element).");

		Map<IpPort, HttpEndpointListener> mih = portListenerMapping.computeIfAbsent(port, k -> new HashMap<>());
		IpPort p = new IpPort(ip, port);
	    HttpEndpointListener hel = mih.get(p);
	    if (hel != null) { // already listen on the same "ip:port"
	        if (equal(sslProvider, hel.getSslProvider())) {
	            return; // O.K. both use the equivalent ssl provider
	        }
	        throw new RuntimeException(format("Lister thread on %s should use the same SSL config", p.toShortString()));
	    }
	    if ((ip == null && !mih.isEmpty())                             // '*:port' vs 'XXX:port'
	      || (ip != null && mih.containsKey(new IpPort((String) null, port)))   // 'XXX:port' vs '*:port'
	      ) {
	        throw new RuntimeException(createDiffInterfacesErrorMsg(p,mih));
	    }

		HttpEndpointListener portListenerThread = new HttpEndpointListener(p, this, sslProvider, timerManager);
		mih.put(p, portListenerThread);
		portListenerThread.start();

		for (IPortChangeListener listener : menuListeners) {
			listener.addPort(port);
		}
	}

	@Override
	public void openPort(SSLableProxy proxy, TimerManager timerManager) throws IOException {
		TimerManager timerManager1 = getRouter() != null ? getRouter().getTimerManager() : null;
		openPort(proxy.getKey().getIp(), proxy.getKey().getPort(), proxy.getSslInboundContext(), timerManager1);
	}

	@Override
	public String getOpenBackendConnections(int port) {
		Map<IpPort, HttpEndpointListener> pl = portListenerMapping.get(port);
		if (pl == null) return "N/A";

		return pl.entrySet().stream()
				.filter(e -> e.getKey().port() == port)
				.findFirst()
				.map(e -> Integer.toString(e.getValue().getNumberOfOpenConnections()))
				.orElseThrow();
	}

	private static String createDiffInterfacesErrorMsg(IpPort p, Map<IpPort, HttpEndpointListener> mih) {
	    final StringBuilder sb = new StringBuilder("Conflict with listening on the same net interfaces [")
	        .append(p.toShortString()).append(", ");
	    for (IpPort ip : mih.keySet()) {
	        sb.append(ip.toShortString()).append(", ");
	    }
		return sb.replace(sb.length() - 2, sb.length(), "]").toString();
	}

	public ExecutorService getExecutorService() {
		return executorService;
	}

	public int getSocketTimeout() {
		return socketTimeout;
	}

	/**
	 * @description Socket timout in ms.
	 * @default 30000
	 */
	@MCAttribute
	public void setSocketTimeout(int timeout) {
		this.socketTimeout = timeout;
	}

	public boolean isTcpNoDelay() {
		return tcpNoDelay;
	}

	/**
	 * @description Whether to use the "TCP no delay" option. (=A TCP/IP packet should be constructed as soon as any
	 *              data has been written to the network buffer. With "TCP no delay" set to false, the network hardware
	 *              waits a short period of time wether the software will write more data. When the packet constructed
	 *              from the data in the buffer would exceed the MTU in size, the packet is always constructed and sent
	 *              immediately.)
	 * @default true
	 */
	@MCAttribute
	public void setTcpNoDelay(boolean tcpNoDelay) {
		this.tcpNoDelay = tcpNoDelay;
	}

	@Override
	public boolean isOpeningPorts() {
		return true;
	}

	public int getForceSocketCloseOnHotDeployAfter() {
		return forceSocketCloseOnHotDeployAfter;
	}

	/**
	 * @description When proxies.xml is changed and &lt;router hotDeploy="true"&gt;, the Spring Context is automatically refreshed,
	 * which restarts the {@link Router} object (=Membrane API Gateway). Before the context refresh, all open socket connections
	 * have to be closed. Exchange objects which are still running might delay this process. Setting forceSocketCloseOnHotDeployAfter
	 * to a non-zero number of milliseconds forces connections to be closed after this time.
	 * @default 30000
	 */
	@MCAttribute
	public void setForceSocketCloseOnHotDeployAfter(int forceSocketCloseOnHotDeployAfter) {
		this.forceSocketCloseOnHotDeployAfter = forceSocketCloseOnHotDeployAfter;
	}

	public int getBacklog() {
		return backlog;
	}

	/**
	 * @description The backlog value passed to {@link java.net.ServerSocket#ServerSocket(int, int, InetAddress)}. The
	 * maximum length of the queue of incoming connections.
	 * @default 50
	 */
	@MCAttribute
	public void setBacklog(int backlog) {
		this.backlog = backlog;
	}
}
