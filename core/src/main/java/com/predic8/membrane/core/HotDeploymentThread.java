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

package com.predic8.membrane.core;

import java.io.File;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.logging.*;

import com.predic8.membrane.core.config.ConfigurationException;

public class HotDeploymentThread extends Thread {

	private static Log log = LogFactory.getLog(HotDeploymentThread.class.getName());

	private Router router;
	private String proxiesFile;
	private long lastModified;

	public HotDeploymentThread(Router router) {
		super("Membrane Hot Deployment Thread");
		this.router = router;
	}

	public void setProxiesFile(String proxiesFile) {
		this.proxiesFile = proxiesFile;
		lastModified = new File(proxiesFile).lastModified();
	}

	@Override
	public void run() {
		log.debug("Hot Deployment Thread started.");
		OUTER:
		while (!isInterrupted()) {
			try {
				while (!configurationChanged()) {
					sleep(1000);
					if (isInterrupted())
						break OUTER;
				}
				

				log.debug("configuration changed. Reloading from " + proxiesFile);

				router.shutdownNoWait();
				router.getConfigurationManager().loadConfiguration(proxiesFile);
				log.info(proxiesFile + " was reloaded.");
			} catch (ConfigurationException e) {
				log.error("Could not redeploy " + proxiesFile + ": " + e.getMessage());
				lastModified = new File(proxiesFile).lastModified();
			} catch (XMLStreamException e) {
				log.error("Could not redeploy " + proxiesFile + ": " + e.getMessage());
				lastModified = new File(proxiesFile).lastModified();
			} catch (InterruptedException e) {				
			} catch (Exception e) {
				log.error("Could not redeploy " + proxiesFile, e);
				lastModified = new File(proxiesFile).lastModified();
			}
		}
		log.debug("Hot Deployment Thread interrupted.");
	}

	private boolean configurationChanged() {
		return new File(proxiesFile).lastModified() > lastModified;
	}

}
