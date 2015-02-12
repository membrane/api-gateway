/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.balancer;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SessionCleanupThread extends Thread {
	private static Log log = LogFactory.getLog(SessionCleanupThread.class.getName());
	public static final long DEFAULT_TIMEOUT = 60 * 60000;

	private Map<String, Cluster> clusters;
	private long sessionTimeout = DEFAULT_TIMEOUT;
	
	public SessionCleanupThread(Map<String, Cluster> clusters) {
		super("SessionCleanupThread");
		this.clusters = clusters;
	}
	
	public void setSessionTimeout(long sessionTimeout) {
		this.sessionTimeout = sessionTimeout;
	}
	
	public long getSessionTimeout() {
		return sessionTimeout;
	}
	
	@Override
	public void run() {
		try {
			sleep(10000); //TODO without exceptions are thrown because log4j is not ready.
		} catch (InterruptedException e1) {
		}
		
		log.debug("SessionCleanupThread started");
		
		while (!interrupted()) {
			synchronized (this) {
				
				long time = System.currentTimeMillis();
				int size = 0;
				int cleaned = 0;
				for (Cluster c : clusters.values()) {
					synchronized (c.getSessions()) {
						size = c.getSessions().size();
						cleaned = cleanupSessions(c);									
					}
				}
				if (cleaned != 0)
					log.debug(""+ cleaned +" sessions removed of "+ size +" in " +(System.currentTimeMillis()-time)+"ms");
			}
			
			try {
				sleep(15000);
			} catch (InterruptedException e) {
			}
		}					
	}

	private int cleanupSessions(Cluster c) {
		Collection<Session> ss = c.getSessions().values();
		Iterator<Session> sIt = ss.iterator();
		int cleaned = 0;
		long now = System.currentTimeMillis();
		
		while (sIt.hasNext()) {
			Session s = sIt.next();
			if (now - s.getLastUsed() > sessionTimeout) {
				cleaned++;
				sIt.remove();
			}
		}
		return cleaned;
	}	
}
