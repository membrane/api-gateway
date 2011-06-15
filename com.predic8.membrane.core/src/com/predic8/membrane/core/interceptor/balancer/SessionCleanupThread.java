package com.predic8.membrane.core.interceptor.balancer;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SessionCleanupThread extends Thread {
	private static Log log = LogFactory.getLog(SessionCleanupThread.class.getName());
	
	Map<String, Cluster> clusters;

	private long sessionTimeout;
	
	public SessionCleanupThread(Map<String, Cluster> clusters, long sessionTimeout) {
		super("SessionCleanupThread");
		this.clusters = clusters;
		this.sessionTimeout = sessionTimeout;
	}
	
	@Override
	public void run() {
		
		log.debug("SessionCleanupThread started");
		
		while (true) {
			synchronized (clusters) {
				
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
				log.warn(e.getMessage()); 
			}
		}					
	}

	private int cleanupSessions(Cluster c) {
		Collection<Session> ss = c.getSessions().values();
		Iterator<Session> sIt = ss.iterator();
		int cleaned = 0;
		
		while (sIt.hasNext()) {
			Session s = sIt.next();
			if ( System.currentTimeMillis()-s.getLastUsed() > sessionTimeout ) {
				cleaned++;
				sIt.remove();
			}
		}
		return cleaned;
	}	
}
