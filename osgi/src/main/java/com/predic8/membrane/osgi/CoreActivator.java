/* Copyright 2009, 2012-2013 predic8 GmbH, www.predic8.com

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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLClassLoader;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

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

	private Router router;
	private ServiceRegistration<?> sr;

	private ILogListener logListener;

	public CoreActivator() {
		logListener = new MembraneLogListener();
	}

	public void start(BundleContext context) throws Exception {
		super.start(context);

		if (new File("configuration/log4j.properties").exists())
			PropertyConfigurator.configure("configuration/log4j.properties");
		
		Platform.addLogListener(logListener);

		final MembraneCommandLine cl = new MembraneCommandLine();
		cl.parse(fixArguments(Platform.getCommandLineArgs()));

		if (cl.hasConfiguration()) {
			log.info("loading monitor beans from command line argument: "
					+ getConfigurationFileName(cl));
			router = Router.init(getConfigurationFileName(cl), this.getClass()
					.getClassLoader());
		} else {
			try {
				if (ClassloaderUtil.fileExists(getConfigurationFileName())) {
					log.info("Eclipse framework found config file: "
							+ getConfigurationFileName());
					readBeanConfigWhenStartedAsProduct();
				} else {
					readBeanConfigWhenStartedInEclipse();
				}
			} catch (Exception e1) {
				log.error("Unable to read bean configuration file: "
						+ e1.getMessage());
				log.error("Unable to read bean configuration file: "
						+ e1.getStackTrace());
				e1.printStackTrace();
			}
		}
		
		sr = context.registerService(router.getClass().getName(), router, null);
	}

	/*
	 * Removes the string "-product" and the next string so that
	 * MembraneCommandLine don't get a parsing error.
	 */
	private String[] fixArguments(String[] args) {
		int i = ArrayUtils.indexOf(args, "-product");
		if (i == -1)
			return args;

		return (String[]) ArrayUtils.remove(
				(String[]) ArrayUtils.remove(args, i), i);
	}

	private String getConfigurationFileName(MembraneCommandLine cl) {
		if (cl.hasConfiguration()) {
			log.info("loading configuration from command line argument: "
					+ cl.getConfiguration());
			return new File(cl.getConfiguration()).getAbsolutePath();
		}
		return System.getProperty("user.home")
				+ System.getProperty("file.separator") + ".membrane.xml";
	}

	private void readBeanConfigWhenStartedAsProduct() throws Exception {
		log.info("Reading router configuration from "
				+ getConfigurationFileName());
		log.info("Project root: " + getProjectRoot());

		URLClassLoader externalClassloader = ClassloaderUtil
				.getExternalClassloader(getProjectRoot());
		// DataSource was unable to load oracle JDBC driver class with external
		// class loader
		// issue at Spring forum:
		// http://forum.springframework.org/showthread.php?t=11141
		Thread.currentThread().setContextClassLoader(externalClassloader);

		router = Router.init(getConfigurationFileName(), externalClassloader);
		log.info("Router instance: " + router);
	}

	private void readBeanConfigWhenStartedInEclipse()
			throws MalformedURLException {
		log.info("Reading configuration from configuration/proxies.xml");

		String membraneHome = System.getenv(Constants.MEMBRANE_HOME);
		if (membraneHome == null)
			throw new IllegalStateException("MEMBRANE_HOME not set");

		router = Router.init(
				"file:" + membraneHome + System.getProperty("file.separator")
						+ "configuration"
						+ System.getProperty("file.separator") + "proxies.xml",
				this.getClass().getClassLoader());
	}

	private String getConfigurationFileName() throws IOException {
		return getProjectRoot() + System.getProperty("file.separator")
				+ "configuration" + System.getProperty("file.separator")
				+ "proxies.xml";
	}

	private String getProjectRoot() throws IOException {
		return new File(FileLocator.resolve(this.getBundle().getEntry("/"))
				.getPath()).getParentFile().getParentFile().getPath();
	}

	public void stop(BundleContext context) throws Exception {
		sr.unregister();
		Platform.removeLogListener(logListener);
		router.stop();
		logListener = null;
		super.stop(context);
	}

}
