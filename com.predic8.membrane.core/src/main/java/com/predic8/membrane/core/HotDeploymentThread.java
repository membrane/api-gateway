package com.predic8.membrane.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class HotDeploymentThread extends Thread {

	private static Log log = LogFactory.getLog(HotDeploymentThread.class
			.getName());

	private Router router;
	private volatile String proxiesFile;
	private volatile long lastModified;

	public HotDeploymentThread(Router router) {
		super("Membrane Hot Deployment Thread");
		this.router = router;
	}

	public void setProxiesFile(String proxiesFile) {
		this.proxiesFile = proxiesFile;
		lastModified = router.getResourceResolver().getTimestamp(proxiesFile);
	}

	@Override
	public void run() {
		log.debug("Hot Deployment Thread started.");
		while (true) {
			try {
				while (!configurationChanged() && !isInterrupted()) {
					sleep(1000);
				}
				
				if (isInterrupted())
					break;

				log.debug("configuration changed. Reloading from "
						+ proxiesFile);

				router.getTransport().closeAll();
				router.getConfigurationManager().loadConfiguration(proxiesFile);
				
				sleep(1000);
			} catch (InterruptedException e) {
				break;
			} catch (Exception e) {
				log.warn("Could not redeploy " + proxiesFile, e);
			}
		}
		log.debug("Hot Deployment Thread interrupted.");
	}

	private boolean configurationChanged() {
		return router.getResourceResolver().getTimestamp(proxiesFile) > lastModified;
	}

}
