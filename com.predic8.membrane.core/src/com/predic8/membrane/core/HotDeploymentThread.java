package com.predic8.membrane.core;

import java.io.File;

import org.apache.commons.logging.*;

public class HotDeploymentThread extends Thread {

	private static Log log = LogFactory.getLog(HotDeploymentThread.class.getName());
	
	private Router router;
	private String proxiesFile;
	private long lastModified; 
	
	public HotDeploymentThread(String proxiesFile, Router router) {
		super("Membrane Hot Deployment Thread");
		
		this.proxiesFile = proxiesFile;
		this.router = router;
		lastModified = new File(proxiesFile).lastModified();
	}
	
	@Override
	public void run() {
		log.debug("Hot Deployment Thread started.");
		try {
			while (!configurationChanged()) {
				sleep(5000);
			}
		
			log.debug("configuration changed. Reloading from "+proxiesFile);
			
			router.getTransport().closeAll();
			router.getConfigurationManager().loadConfiguration(proxiesFile);			
		} catch (Exception e) {
			log.warn("Could not redeploy "+proxiesFile,e);
		}
	}

	private boolean configurationChanged() {
		return new File(proxiesFile).lastModified() > lastModified;
	}

}
