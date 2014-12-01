/* Copyright 2013 predic8 GmbH, www.predic8.com

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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.support.AbstractRefreshableApplicationContext;

import com.predic8.membrane.core.config.spring.CheckableBeanFactory;
import com.predic8.membrane.core.config.spring.CheckableBeanFactory.InvalidConfigurationException;

public class HotDeploymentThread extends Thread {

	private static Log log = LogFactory.getLog(HotDeploymentThread.class.getName());

	private List<HotDeploymentThread.FileInfo> files = new ArrayList<HotDeploymentThread.FileInfo>();
	protected AbstractRefreshableApplicationContext applicationContext;
	private boolean reloading;
	
	private static class FileInfo {
		public String file;
		public long lastModified;
	}


	public HotDeploymentThread(AbstractRefreshableApplicationContext applicationContext) {
		super("Membrane Hot Deployment Thread");
		this.applicationContext = applicationContext;
	}

	public void setFiles(List<File> files) {
		this.files.clear();
		for (File file : files) {
			HotDeploymentThread.FileInfo fi = new FileInfo();
			fi.file = file.getAbsolutePath();
			this.files.add(fi);
		}
		updateLastModified();
	}

	private void updateLastModified() {
		for (HotDeploymentThread.FileInfo fi : files)
			fi.lastModified = new File(fi.file).lastModified();
	}

	private boolean configurationChanged() {
		for (HotDeploymentThread.FileInfo fi : files)
			if (new File(fi.file).lastModified() > fi.lastModified)
				return true;
		return false;
	}

	@Override
	public void run() {
		log.debug("Spring Hot Deployment Thread started.");
		while (!isInterrupted()) {
			try {
				while (!configurationChanged()) {
					sleep(1000);
				}

				log.debug("spring configuration changed.");

				if (applicationContext instanceof CheckableBeanFactory)
					((CheckableBeanFactory)applicationContext).checkForInvalidBeanDefinitions();
				
				reload();
				
				break;
			} catch (InvalidConfigurationException e) {
				log.error(e.getMessage());
				log.error("Application context was NOT restarted. Please fix the error in the configuration file.");
				updateLastModified();
			} catch (InterruptedException e) {
			    // #162 HotDeploymentThread don't stop on Interrupt.
			    // InterruptedException clears interrupt flag. see javadoc Thread.interrupt();
			    // So reset it.
			    interrupt(); 
			} catch (Exception e) {
				log.error("Could not redeploy.", e);
				updateLastModified();
			}
		}
		log.debug("Spring Hot Deployment Thread interrupted.");
	}

	protected void reload() throws Exception {
		synchronized(this) {
			reloading = true;
		}
		applicationContext.stop();
		applicationContext.refresh();
		applicationContext.start();
	}
	
	public void stopASAP() {
		synchronized (this) {
			if (reloading)
				return;
		}
		interrupt();
	}
}