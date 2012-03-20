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

package com.predic8.membrane.osgi;

import java.io.*;
import java.net.*;
import java.util.concurrent.Executors;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.*;
import org.eclipse.core.runtime.*;
import org.osgi.framework.BundleContext;

import com.predic8.membrane.core.ClassloaderUtil;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.MembraneCommandLine;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.osgi.logger.MembraneLogListener;

/**
 * The activator class controls the plug-in life cycle
 */
public class CoreActivator extends Plugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.predic8.membrane.core";

	private static Log log = LogFactory.getLog(CoreActivator.class.getName());

	// The shared instance
	private static CoreActivator plugin;

	private ILogListener logListener;
	
	public CoreActivator() {
		logListener = new MembraneLogListener();
	}

	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;

		Platform.addLogListener(logListener);
		
		final MembraneCommandLine cl = new MembraneCommandLine();
		cl.parse(fixArguments(Platform.getCommandLineArgs()));
		
		if (cl.hasMonitorBeans()) {
			log.info("loading monitor beans from command line argument: "+cl.getMonitorBeans());
			Router.init(new File(cl.getMonitorBeans()).getAbsolutePath(), 
						this.getClass().getClassLoader());
		} else {		
			try {
				if (ClassloaderUtil.fileExists(getMonitorBeansFileName())) {
					log.info("Eclipse framework found config file: " + getMonitorBeansFileName());
					readBeanConfigWhenStartedAsProduct();
				} else {
					readBeanConfigWhenStartedInEclipse();
				}
			} catch (Exception e1) {
				log.error("Unable to read bean configuration file: " + e1.getMessage());
				log.error("Unable to read bean configuration file: " + e1.getStackTrace());
				e1.printStackTrace();
			}
		}

		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					Router.getInstance().getConfigurationManager().loadConfiguration(getConfigurationFileName(cl));
				} catch (Exception e) {
					log.warn("no configuration loaded", e);
					// we ignore this exception because the monitor can start up
					// without loading a rules configuration
					// we need to throw an exception because the router must display an error message
				}
			}

			private String getConfigurationFileName(MembraneCommandLine cl) {
				if (cl.hasConfiguration()) {
					log.info("loading configuration from command line argument: "+cl.getConfiguration());
					return new File(cl.getConfiguration()).getAbsolutePath();
				}
				return System.getProperty("user.home") + System.getProperty("file.separator") + ".membrane.xml";
			}
		});
	}

	/*
	 * Removes the string "-product" and the next string so that MembraneCommandLine don't get a parsing error. 
	 */
	private String[] fixArguments(String[] args) {
		int i = ArrayUtils.indexOf(args, "-product");
		if (i == -1)
			return args;
		
		return (String[])ArrayUtils.remove( (String[])ArrayUtils.remove(args, i),i);
	}

	private void readBeanConfigWhenStartedAsProduct() throws Exception {
		log.info("Reading router configuration from " + getMonitorBeansFileName());
		log.info("Project root: " + getProjectRoot());
		
		URLClassLoader externalClassloader = ClassloaderUtil.getExternalClassloader(getProjectRoot());
		//DataSource was unable to load oracle JDBC driver class with external class loader
		//issue at Spring forum:  http://forum.springframework.org/showthread.php?t=11141
		Thread.currentThread().setContextClassLoader(externalClassloader);
		
		Router.init(getMonitorBeansFileName(), externalClassloader );
		log.info("Router instance: " + Router.getInstance());
	}

	private void readBeanConfigWhenStartedInEclipse() throws MalformedURLException {
		log.info("Reading configuration from configuration/monitor-beans.xml");

		String membraneHome = System.getenv(Constants.MEMBRANE_HOME);
		if (membraneHome == null)
			throw new IllegalStateException("MEMBRANE_HOME not set"); 		
		
		Router.init("file:" + membraneHome + System.getProperty("file.separator") + "configuration" + System.getProperty("file.separator") + "monitor-beans.xml", this.getClass().getClassLoader());
	}

	private String getMonitorBeansFileName() throws IOException {
		return getProjectRoot() + System.getProperty("file.separator") + "configuration" + System.getProperty("file.separator") + "monitor-beans.xml";
	}

	private String getProjectRoot() throws IOException {
		return new File(FileLocator.resolve(CoreActivator.getDefault().getBundle().getEntry("/")).getPath()).getParentFile().getParentFile().getPath();
	}
	
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		Platform.removeLogListener(logListener);
		logListener = null;
		super.stop(context);
	}

	
	public static CoreActivator getDefault() {
		return plugin;
	}

}
