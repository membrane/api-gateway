/* Copyright 2009 predic8 GmbH, www.predic8.com

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
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class CoreActivator extends Plugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.predic8.membrane.core";

	private static Log log = LogFactory.getLog(CoreActivator.class.getName());

	private static ILog pluginLogger;

	// The shared instance
	private static CoreActivator plugin;

	
	public CoreActivator() {

	}

	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;

		pluginLogger = CoreActivator.getDefault().getLog();

		//String path = new File(FileLocator.resolve(CoreActivator.getDefault().getBundle().getEntry("/")).getPath()).getAbsolutePath();
		
		try {
			if (ClassloaderUtil.fileExists(getConfigFileName())) {
				info("Eclipse framework found config file: " + getConfigFileName());
				readBeanConfigWhenStartedAsProduct();
			} else {
				readBeanConfigWhenStartedInEclipse();
			}
		} catch (Exception e1) {
			error("Unable to read bean configuration file: " + e1.getMessage());
			error("Unable to read bean configuration file: " + e1.getStackTrace());
			e1.printStackTrace();
		}

		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					Router.getInstance().getConfigurationManager().loadConfiguration(System.getProperty("user.home") + System.getProperty("file.separator") + ".membrane.xml");
				} catch (Exception e) {
					// we ignore this exception because the monitor can start up
					// without loading a rules configuration
					// we need to throw an exception because the router must display an error message
				}
			}
		});
	}

	private void readBeanConfigWhenStartedAsProduct() throws Exception {
		info("Reading router configuration from " + getConfigFileName());
		log.debug("Reading configuration from " + getConfigFileName());
		info("Project root: " + getProjectRoot());
		Router.init(getConfigFileName(), ClassloaderUtil.getExternalClassloader(getProjectRoot()) );
		info("Router instance: " + Router.getInstance());
	}

	private void readBeanConfigWhenStartedInEclipse() throws MalformedURLException {
		log.debug("Reading configuration from configuration/monitor-beans.xml");
		error("Reading configuration from configuration/monitor-beans.xml");

		String membraneHome = System.getenv("MEMBRANE_HOME");
		if (membraneHome == null)
			throw new IllegalStateException("membarne_home not set"); 		
		
		Router.init("file:" + membraneHome + System.getProperty("file.separator") + "configuration" + System.getProperty("file.separator") + "monitor-beans.xml", this.getClass().getClassLoader());
	}

	private void error(String message) {
		pluginLogger.log(new Status(IStatus.ERROR, CoreActivator.PLUGIN_ID, message));
	}

	private void info(String message) {
		pluginLogger.log(new Status(IStatus.INFO, CoreActivator.PLUGIN_ID, message));
	}
	
	private String getConfigFileName() throws IOException {
		return getProjectRoot() + System.getProperty("file.separator") + "configuration" + System.getProperty("file.separator") + "monitor-beans.xml";
	}

	private String getProjectRoot() throws IOException {
		return new File(FileLocator.resolve(CoreActivator.getDefault().getBundle().getEntry("/")).getPath()).getParentFile().getParentFile().getPath();
	}
	
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	
	public static CoreActivator getDefault() {
		return plugin;
	}

}
