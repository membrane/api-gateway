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
				log.debug("cleanup started");
				
				long time = System.currentTimeMillis();
				int size = 0;
				int cleaned = 0;
				for (Cluster c : clusters.values()) {
					synchronized (c.getSessions()) {
						size = c.getSessions().size();
						cleaned = cleanupSessions(c);									
					}
				}
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
